import { buildSessionCookie, createSession } from "../../_lib/session";

interface Env {
    ADMIN_PASSWORD: string;
    SITE_SESSION_SECRET: string;
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    try {
        const body = await request.json() as { username?: string; password?: string };
        const username = body.username?.trim().toLowerCase();
        const password = body.password?.trim();

        if (!username || !password) {
            return Response.json({ ok: false, error: "Username and password are required" }, { status: 400 });
        }

        if (username !== "mrai" || password !== env.ADMIN_PASSWORD) {
            return Response.json({ ok: false, error: "Invalid admin credentials" }, { status: 401 });
        }

        const token = await createSession(env.SITE_SESSION_SECRET, { role: "admin", username: "mrai" });
        const headers = new Headers();
        headers.append("Set-Cookie", buildSessionCookie(request, token));

        return new Response(JSON.stringify({ ok: true }), { status: 200, headers });
    } catch (error) {
        console.error("admin-login failed", error);
        return Response.json({ ok: false, error: "Bad request" }, { status: 400 });
    }
};
