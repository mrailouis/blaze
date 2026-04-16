import { refreshPlayerOverrideName } from "../../_lib/playerOverrides";

interface Env {
    DB: D1Database;
}

export const onRequestPost = async (context: { request: Request; env: Env }) => {
    try {
        const body = await context.request.json() as {
            uuid?: string;
            key?: string;
            minecraftName?: string;
            modVersion?: string;
        };

        const uuid = body.uuid?.trim();
        const key = body.key?.trim();
        const minecraftName = body.minecraftName?.trim() ?? null;
        const modVersion = body.modVersion?.trim() ?? null;

        if (!uuid || !key) {
            return Response.json(
                { allowed: false, reason: "missing uuid or key" },
                { status: 400 }
            );
        }

        const license = await context.env.DB
            .prepare(`
                SELECT license_key, bound_uuid, status, expires_at, product_tier
                FROM licenses
                WHERE license_key = ?
            `)
            .bind(key)
            .first<{
                license_key: string;
                bound_uuid: string | null;
                status: string;
                expires_at: string;
                product_tier: string;
            }>();

        if (!license) {
            return Response.json({ allowed: false, reason: "invalid key" });
        }

        if (license.status !== "active") {
            return Response.json({ allowed: false, reason: "inactive license" });
        }

        if (license.expires_at !== "never") {
            const now = new Date();
            const expiry = new Date(license.expires_at);

            if (expiry.getTime() < now.getTime()) {
                return Response.json({
                    allowed: false,
                    reason: "license expired"
                });
            }
        }

        if (license.bound_uuid && license.bound_uuid !== uuid) {
            return Response.json({ allowed: false, reason: "uuid mismatch" });
        }

        if (!license.bound_uuid) {
            await context.env.DB.prepare(`
                UPDATE licenses
                SET bound_uuid = ?, last_seen_name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE license_key = ?
            `).bind(uuid, minecraftName, key).run();
        } else {
            await context.env.DB.prepare(`
                UPDATE licenses
                SET last_seen_name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE license_key = ?
            `).bind(minecraftName, key).run();
        }

        await context.env.DB.prepare(`
            INSERT INTO auth_logs (license_key, uuid, minecraft_name, mod_version, success, reason, ip)
            VALUES (?, ?, ?, ?, 1, 'ok', ?)
        `).bind(
            key,
            uuid,
            minecraftName,
            modVersion,
            context.request.headers.get("CF-Connecting-IP")
        ).run();

        await refreshPlayerOverrideName(context.env.DB, uuid, minecraftName);

        return Response.json({
            allowed: true,
            reason: "ok",
            expiresAt: license.expires_at,
            boundUuid: license.bound_uuid ?? uuid,
            productTier: license.product_tier
        });
    } catch {
        return Response.json(
            { allowed: false, reason: "server error" },
            { status: 500 }
        );
    }
};
