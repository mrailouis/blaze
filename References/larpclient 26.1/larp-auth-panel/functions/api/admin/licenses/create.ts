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
            licenseKey?: string;
            boundUuid?: string | null;
            discordName?: string;
            status?: string;
            expiresAt?: string;
            productTier?: string;
        };

        const licenseKey = body.licenseKey?.trim();
        const boundUuid = body.boundUuid?.trim() || null;
        const discordName = body.discordName?.trim();
        const status = body.status?.trim() || "active";
        const expiresAt = body.expiresAt?.trim() || "never";
        const productTier = body.productTier?.trim() || "addon";

        if (!licenseKey) {
            return Response.json({ ok: false, error: "Missing licenseKey" }, { status: 400 });
        }

        if (!discordName) {
            return Response.json({ ok: false, error: "Missing discordName" }, { status: 400 });
        }

        await env.DB.prepare(`
            INSERT INTO licenses (license_key, bound_uuid, last_seen_name, status, expires_at, product_tier, discord_name)
            VALUES (?, ?, NULL, ?, ?, ?, ?)
        `).bind(licenseKey, boundUuid, status, expiresAt, productTier, discordName).run();

        return Response.json({ ok: true });
    } catch {
        return Response.json({ ok: false, error: "Create failed" }, { status: 500 });
    }
};
