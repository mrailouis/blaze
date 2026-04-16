import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";
import { resolvePlayerIdentity } from "../../../_lib/playerOverrides";

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
        const body = await request.json() as { uuid?: string };
        const resolved = await resolvePlayerIdentity(env.DB, body.uuid);

        if (!resolved.uuid) {
            return Response.json({ ok: false, error: "Invalid UUID" }, { status: 400 });
        }

        return Response.json({
            ok: true,
            uuid: resolved.uuid,
            minecraftName: resolved.minecraftName,
            source: resolved.source
        });
    } catch {
        return Response.json({ ok: false, error: "Lookup failed" }, { status: 500 });
    }
};
