export async function getSession(): Promise<boolean> {
    const res = await fetch("/api/admin/session", {
        credentials: "include"
    });

    const data = await res.json();
    return !!data.authenticated;
}

export async function logout(): Promise<void> {
    await fetch("/api/admin/logout", {
        method: "POST",
        credentials: "include"
    });
}