import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";

interface Env {
    DB: D1Database;
    ADMIN_SESSION_SECRET: string;
}

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const token = readCookie(request, "larp_admin_session");
    const authed = await verifyAdminSession(env.ADMIN_SESSION_SECRET, token);

    if (!authed) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    const result = await env.DB.prepare(`
    SELECT id, license_key, bound_uuid, discord_name, portal_password_set_at, last_seen_name, last_seen_client_type, status, expires_at, product_tier, updated_at
    FROM licenses
    ORDER BY id DESC
  `).all();

    return Response.json({
        ok: true,
        licenses: result.results ?? []
    });
};
