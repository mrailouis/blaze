export type PortalLicenseRow = {
    license_key: string;
    status: string;
    expires_at: string;
    product_tier: string;
    discord_name: string | null;
    portal_password_hash: string | null;
    portal_password_salt: string | null;
    portal_password_set_at: string | null;
};

export type PortalLicenseResult =
    | { ok: true; license: PortalLicenseRow }
    | { ok: false; error: string; status: number };

export const MIN_PORTAL_PASSWORD_LENGTH = 8;
// Cloudflare Pages Functions rejects PBKDF2 iteration counts above 100000.
export const PORTAL_PASSWORD_ITERATIONS = 100_000;

const encoder = new TextEncoder();

function toBase64Url(bytes: Uint8Array): string {
    let binary = "";
    for (const value of bytes) {
        binary += String.fromCharCode(value);
    }

    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64Url(input: string): Uint8Array {
    const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }

    return bytes;
}

export function normalizeDiscordName(value: string | null | undefined): string {
    return value?.trim().toLowerCase() ?? "";
}

export function hasPortalPassword(license: Pick<PortalLicenseRow, "portal_password_hash" | "portal_password_salt">): boolean {
    return Boolean(license.portal_password_hash && license.portal_password_salt);
}

export async function findPortalLicense(db: D1Database, licenseKey: string): Promise<PortalLicenseRow | null> {
    return db.prepare(`
        SELECT license_key, status, expires_at, product_tier, discord_name, portal_password_hash, portal_password_salt, portal_password_set_at
        FROM licenses
        WHERE license_key = ?
    `).bind(licenseKey).first<PortalLicenseRow>();
}

export async function validatePortalLicense(
    db: D1Database,
    licenseKey: string,
    discordName: string
): Promise<PortalLicenseResult> {
    const trimmedLicenseKey = licenseKey.trim();
    const trimmedDiscordName = discordName.trim();

    if (!trimmedLicenseKey) {
        return { ok: false, error: "Missing license key", status: 400 };
    }

    if (!trimmedDiscordName) {
        return { ok: false, error: "Missing Discord name", status: 400 };
    }

    const license = await findPortalLicense(db, trimmedLicenseKey);

    if (!license) {
        return { ok: false, error: "Invalid license or Discord name", status: 401 };
    }

    if (license.status !== "active") {
        return { ok: false, error: "License is not active", status: 401 };
    }

    if (license.expires_at !== "never" && new Date(license.expires_at).getTime() < Date.now()) {
        return { ok: false, error: "License has expired", status: 401 };
    }

    if (license.product_tier !== "addon") {
        return { ok: false, error: "This license does not grant addon access", status: 403 };
    }

    if (normalizeDiscordName(license.discord_name) !== normalizeDiscordName(trimmedDiscordName)) {
        return { ok: false, error: "Invalid license or Discord name", status: 401 };
    }

    return {
        ok: true,
        license: {
            ...license,
            discord_name: trimmedDiscordName
        }
    };
}

async function derivePasswordBytes(password: string, salt: string, iterations: number): Promise<Uint8Array> {
    const key = await crypto.subtle.importKey(
        "raw",
        encoder.encode(password),
        "PBKDF2",
        false,
        ["deriveBits"]
    );

    const derivedBits = await crypto.subtle.deriveBits(
        {
            name: "PBKDF2",
            hash: "SHA-256",
            salt: fromBase64Url(salt),
            iterations
        },
        key,
        256
    );

    return new Uint8Array(derivedBits);
}

export function createPasswordSalt(): string {
    return toBase64Url(crypto.getRandomValues(new Uint8Array(16)));
}

export async function hashPortalPassword(
    password: string,
    salt: string,
    iterations = PORTAL_PASSWORD_ITERATIONS
): Promise<string> {
    return toBase64Url(await derivePasswordBytes(password, salt, iterations));
}

export async function verifyPortalPassword(
    password: string,
    salt: string,
    expectedHash: string,
    iterations = PORTAL_PASSWORD_ITERATIONS
): Promise<boolean> {
    const actualHash = await hashPortalPassword(password, salt, iterations);
    return actualHash === expectedHash;
}
