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
        const body = await request.json() as {
            id?: number;
            status?: string;
            expiresAt?: string;
            boundUuid?: string | null;
            discordName?: string;
            productTier?: string;
            resetPortalPassword?: boolean;
        };

        if (!body.id) {
            return Response.json({ ok: false, error: "Missing id" }, { status: 400 });
        }

        const status = body.status?.trim() || "active";
        const expiresAt = body.expiresAt?.trim() || "never";
        const boundUuid = body.boundUuid?.trim() || null;
        const discordName = body.discordName?.trim() || null;
        const productTier = body.productTier?.trim() || "addon";
        const resetPortalPassword = body.resetPortalPassword ? 1 : 0;

        await env.DB.prepare(`
            UPDATE licenses
            SET status = ?,
                expires_at = ?,
                bound_uuid = ?,
                product_tier = ?,
                discord_name = ?,
                portal_password_hash = CASE WHEN ? = 1 THEN NULL ELSE portal_password_hash END,
                portal_password_salt = CASE WHEN ? = 1 THEN NULL ELSE portal_password_salt END,
                portal_password_set_at = CASE WHEN ? = 1 THEN NULL ELSE portal_password_set_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        `).bind(status, expiresAt, boundUuid, productTier, discordName, resetPortalPassword, resetPortalPassword, resetPortalPassword, body.id).run();

        return Response.json({ ok: true });
    } catch {
        return Response.json({ ok: false, error: "Update failed" }, { status: 500 });
    }
};
