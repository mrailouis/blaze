import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";
import { listPlayerOverrides } from "../../../_lib/playerOverrides";

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

    const rows = await listPlayerOverrides(env.DB);

    return Response.json({
        ok: true,
        overrides: rows.map((row) => ({
            id: row.id,
            uuid: row.target_uuid,
            minecraftName: row.target_name,
            customName: row.custom_name,
            scaleX: Number(row.scale_x ?? 1),
            scaleY: Number(row.scale_y ?? 1),
            scaleZ: Number(row.scale_z ?? 1),
            enabled: row.enabled === 1,
            lastResolvedAt: row.last_resolved_at,
            updatedAt: row.updated_at
        }))
    });
};
