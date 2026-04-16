export type SiteSessionPayload = {
    role: "admin" | "license";
    licenseKey?: string;
    productTier?: string;
    discordName?: string;
    portalPasswordSetAt?: string | null;
    username?: string;
    iat: number;
    exp: number;
};

const encoder = new TextEncoder();

function toBase64Url(bytes: Uint8Array): string {
    let binary = "";
    for (const b of bytes) binary += String.fromCharCode(b);
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

async function importKey(secret: string): Promise<CryptoKey> {
    return crypto.subtle.importKey(
        "raw",
        encoder.encode(secret),
        { name: "HMAC", hash: "SHA-256" },
        false,
        ["sign", "verify"]
    );
}

async function sign(secret: string, data: string): Promise<string> {
    const key = await importKey(secret);
    const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(data));
    return toBase64Url(new Uint8Array(signature));
}

async function verify(secret: string, data: string, signature: string): Promise<boolean> {
    const key = await importKey(secret);
    return crypto.subtle.verify(
        "HMAC",
        key,
        fromBase64Url(signature),
        encoder.encode(data)
    );
}

export async function createSession(
    secret: string,
    payload: Omit<SiteSessionPayload, "iat" | "exp">,
    maxAgeSeconds = 60 * 60 * 8
): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    const finalPayload: SiteSessionPayload = {
        ...payload,
        iat: now,
        exp: now + maxAgeSeconds
    };

    const payloadEncoded = toBase64Url(encoder.encode(JSON.stringify(finalPayload)));
    const signature = await sign(secret, payloadEncoded);
    return `${payloadEncoded}.${signature}`;
}

export async function verifySession(secret: string, token: string | null | undefined): Promise<SiteSessionPayload | null> {
    if (!token) return null;

    const parts = token.split(".");
    if (parts.length !== 2) return null;

    const [payloadEncoded, signature] = parts;
    const ok = await verify(secret, payloadEncoded, signature);
    if (!ok) return null;

    try {
        const payloadJson = new TextDecoder().decode(fromBase64Url(payloadEncoded));
        const payload = JSON.parse(payloadJson) as SiteSessionPayload;
        const now = Math.floor(Date.now() / 1000);
        if (payload.exp <= now) return null;
        if (payload.role !== "admin" && payload.role !== "license") return null;
        return payload;
    } catch {
        return null;
    }
}

export function readCookie(request: Request, name: string): string | null {
    const cookieHeader = request.headers.get("Cookie") ?? "";
    const cookies = cookieHeader.split(";").map((entry) => entry.trim());

    for (const cookie of cookies) {
        const [key, ...rest] = cookie.split("=");
        if (key === name) {
            return rest.join("=") || null;
        }
    }

    return null;
}

export async function getSessionFromRequest(request: Request, secret: string): Promise<SiteSessionPayload | null> {
    const token = readCookie(request, "larp_site_session");
    return verifySession(secret, token);
}

export function buildSessionCookie(request: Request, token: string, maxAgeSeconds = 60 * 60 * 8): string {
    const isHttps = new URL(request.url).protocol === "https:";
    return (
        `larp_site_session=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAgeSeconds}` +
        (isHttps ? "; Secure" : "")
    );
}

export function buildLogoutCookie(request: Request): string {
    const isHttps = new URL(request.url).protocol === "https:";
    return (
        `larp_site_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0` +
        (isHttps ? "; Secure" : "")
    );
}
