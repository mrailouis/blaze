import { getSiteSession } from "../../_lib/site";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
}

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const session = await getSiteSession(request, env);

    return Response.json({
        ok: true,
        session: session
            ? {
                role: session.role,
                productTier: session.productTier ?? null,
                discordName: session.discordName ?? null,
                username: session.username ?? null
            }
            : null
    });
};
