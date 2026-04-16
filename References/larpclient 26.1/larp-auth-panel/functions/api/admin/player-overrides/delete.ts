import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";

interface Env {
    DB: D1Database;
    ADMIN_SESSION_SECRET: string;
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    const token = readCookie(request, "larp_admin_session");
    const authed = await verifyAdminSession(env.ADMIN_SESSION_SECRET, token);

    if (!authed) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    try {
        const body = await request.json() as { id?: number };
        if (!body.id) {
            return Response.json({ ok: false, error: "Missing id" }, { status: 400 });
        }

        await env.DB.prepare(`
            DELETE FROM player_overrides
            WHERE id = ?
        `).bind(body.id).run();

        return Response.json({ ok: true });
    } catch {
        return Response.json({ ok: false, error: "Delete failed" }, { status: 500 });
    }
};
