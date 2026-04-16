import { readCookie, verifyAdminSession } from "../../_lib/adminAuth";

interface Env {
    ADMIN_SESSION_SECRET: string;
}

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const token = readCookie(request, "larp_admin_session");
    const authenticated = await verifyAdminSession(env.ADMIN_SESSION_SECRET, token);

    return Response.json({
        ok: true,
        authenticated
    });
};