import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";

interface Env {
    DB: D1Database;
    ADMIN_SESSION_SECRET: string;
}

type CountRow = { count: number };
type OnlineUserRow = {
    uuid: string;
    minecraft_name: string | null;
    last_seen_at: string;
    client_type: string;
    license_key: string | null;
    product_tier: string | null;
};

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const token = readCookie(request, "larp_admin_session");
    const authed = await verifyAdminSession(env.ADMIN_SESSION_SECRET, token);

    if (!authed) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    const active = await env.DB.prepare(`
    SELECT COUNT(*) as count
    FROM licenses
    WHERE status = 'active'
  `).first<CountRow>();

    const revoked = await env.DB.prepare(`
    SELECT COUNT(*) as count
    FROM licenses
    WHERE status = 'revoked'
  `).first<CountRow>();

    const onlineUsers = await env.DB.prepare(`
    SELECT s.uuid, s.minecraft_name, s.last_seen_at, s.client_type, s.license_key, l.product_tier
    FROM client_sessions s
    LEFT JOIN licenses l ON l.license_key = s.license_key
    WHERE datetime(s.last_seen_at) >= datetime('now', '-45 seconds')
    ORDER BY datetime(s.last_seen_at) DESC
  `).all<OnlineUserRow>();

    const rows = onlineUsers.results ?? [];
    const modOnline = rows.filter((row) => row.client_type === "larp-mod").length;
    const addonOnline = rows.filter((row) => row.client_type === "larp-addon").length;

    return Response.json({
        ok: true,
        onlineUsers: rows,
        onlineTotals: {
            total: rows.length,
            mod: modOnline,
            addon: addonOnline
        },
        activeLicenses: active?.count ?? 0,
        revokedLicenses: revoked?.count ?? 0
    });
};
