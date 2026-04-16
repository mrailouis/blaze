function getErrorMessage(data: unknown, fallback: string): string {
    if (typeof data === "object" && data !== null && "error" in data && typeof data.error === "string") {
        return data.error;
    }

    return fallback;
}

export async function apiPost<T>(url: string, body: unknown): Promise<T> {
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body)
    });

    const text = await res.text();
    let data: unknown = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(`Non-JSON response from ${url}: ${text.slice(0, 200)}`);
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

    const text = await res.text();
    let data: unknown = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(`Non-JSON response from ${url}: ${text.slice(0, 200)}`);
        }
    }

    if (!res.ok) {
        throw new Error(getErrorMessage(data, `Request failed (${res.status})`));
    }

    return data as T;
}
