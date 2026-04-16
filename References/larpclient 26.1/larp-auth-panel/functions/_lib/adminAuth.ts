export interface AdminEnv {
    ADMIN_SESSION_SECRET: string;
}

type SessionPayload = {
    role: "admin";
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

export async function createAdminSession(secret: string, maxAgeSeconds = 60 * 60 * 8): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    const payload: SessionPayload = {
        role: "admin",
        iat: now,
        exp: now + maxAgeSeconds
    };

    const payloadJson = JSON.stringify(payload);
    const payloadEncoded = toBase64Url(encoder.encode(payloadJson));
    const signature = await sign(secret, payloadEncoded);

    return `${payloadEncoded}.${signature}`;
}

export async function verifyAdminSession(secret: string, token: string | null | undefined): Promise<boolean> {
    if (!token) return false;

    const parts = token.split(".");
    if (parts.length !== 2) return false;

    const [payloadEncoded, signature] = parts;

    const ok = await verify(secret, payloadEncoded, signature);
    if (!ok) return false;

    try {
        const payloadJson = new TextDecoder().decode(fromBase64Url(payloadEncoded));
        const payload = JSON.parse(payloadJson) as SessionPayload;

        if (payload.role !== "admin") return false;

        const now = Math.floor(Date.now() / 1000);
        if (payload.exp <= now) return false;

        return true;
    } catch {
        return false;
    }
}

export function readCookie(request: Request, name: string): string | null {
    const cookieHeader = request.headers.get("Cookie") ?? "";
    const cookies = cookieHeader.split(";").map((c) => c.trim());

    for (const cookie of cookies) {
        const [key, ...rest] = cookie.split("=");
        if (key === name) {
            return rest.join("=") || null;
        }
    }

    return null;
}

export function buildSessionCookie(request: Request, token: string, maxAgeSeconds = 60 * 60 * 8): string {
    const isHttps = new URL(request.url).protocol === "https:";
    return (
        `larp_admin_session=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAgeSeconds}` +
        (isHttps ? `; Secure` : ``)
    );
}

export function buildLogoutCookie(request: Request): string {
    const isHttps = new URL(request.url).protocol === "https:";
    return (
        `larp_admin_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0` +
        (isHttps ? `; Secure` : ``)
    );
}