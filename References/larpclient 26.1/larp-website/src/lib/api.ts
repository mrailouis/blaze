import type { BootstrapData, SessionInfo } from "../types";

function getErrorMessage(data: unknown, fallback: string): string {
    if (typeof data === "object" && data !== null && "error" in data && typeof data.error === "string") {
        return data.error;
    }

    return fallback;
}

async function parseResponse<T>(res: Response): Promise<T> {
    const text = await res.text();
    let data: unknown = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(`Non-JSON response (${res.status}): ${text.slice(0, 200)}`);
        }
    }

    if (!res.ok) {
        throw new Error(getErrorMessage(data, `Request failed (${res.status})`));
    }

    return data as T;
}

export async function apiGet<T>(url: string): Promise<T> {
    const res = await fetch(url, {
        credentials: "include"
    });

    return parseResponse<T>(res);
}

export async function apiPost<T>(url: string, body: unknown): Promise<T> {
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body)
    });

    return parseResponse<T>(res);
}

export async function apiDelete<T>(url: string, body: unknown): Promise<T> {
    const res = await fetch(url, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body)
    });

    return parseResponse<T>(res);
}

export async function apiForm<T>(url: string, formData: FormData): Promise<T> {
    const res = await fetch(url, {
        method: "POST",
        credentials: "include",
        body: formData
    });

    return parseResponse<T>(res);
}

export async function getSession(): Promise<SessionInfo> {
    const data = await apiGet<{ ok: true; session: SessionInfo }>("/api/auth/session");
    return data.session;
}

export async function getLicenseStatus(licenseKey: string, discordName: string): Promise<{
    ok: true;
    passwordSet: boolean;
    licenseKey: string;
    discordName: string | null;
}> {
    return apiPost("/api/auth/license-status", { licenseKey, discordName });
}

export async function logout(): Promise<void> {
    await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include"
    });
}

export async function getBootstrap(): Promise<BootstrapData> {
    return apiGet<BootstrapData>("/api/content/bootstrap");
}
