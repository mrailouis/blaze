import { listWireOverrides, refreshPlayerOverrideName, resolveSelfPlayerCustomization } from "../../_lib/playerOverrides";

interface Env {
    DB: D1Database;
}

type ClientType = "larp-mod" | "larp-addon";

type LicenseRow = {
    license_key: string;
    bound_uuid: string | null;
    status: string;
    expires_at: string;
};

function resolveClientType(value?: string | null): ClientType {
    return value?.trim() === "larp-mod" ? "larp-mod" : "larp-addon";
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    try {
        const body = await request.json() as {
            uuid?: string;
            key?: string;
            minecraftName?: string;
            modVersion?: string;
            clientType?: string;
            selfCustomization?: {
                customName?: string | null;
                scaleX?: number;
                scaleY?: number;
                scaleZ?: number;
                showToOthers?: boolean;
                applyChanges?: boolean;
            };
        };

        const uuid = body.uuid?.trim();
        const key = body.key?.trim();
        const minecraftName = body.minecraftName?.trim() ?? null;
        const modVersion = body.modVersion?.trim() ?? null;
        const clientType = resolveClientType(body.clientType);

        if (!uuid) {
            return Response.json(
                { ok: false, error: "Missing uuid" },
                { status: 400 }
            );
        }

        const nowIso = new Date().toISOString();
        let licenseKeyForSession: string | null = null;

        if (clientType === "larp-addon") {
            if (!key) {
                return Response.json({ ok: false, error: "Missing key for addon heartbeat" }, { status: 400 });
            }

            const license = await env.DB.prepare(`
                SELECT license_key, bound_uuid, status, expires_at
                FROM licenses
                WHERE license_key = ?
            `).bind(key).first<LicenseRow>();

            if (!license) {
                return Response.json({ ok: false, error: "Invalid key" }, { status: 401 });
            }

            if (license.status !== "active") {
                return Response.json({ ok: false, error: "Inactive license" }, { status: 401 });
            }

            if (license.expires_at !== "never") {
                const now = new Date();
                const expiry = new Date(license.expires_at);

                if (expiry.getTime() < now.getTime()) {
                    return Response.json({ ok: false, error: "Expired" }, { status: 401 });
                }
            }

            if (license.bound_uuid && license.bound_uuid !== uuid) {
                return Response.json({ ok: false, error: "UUID mismatch" }, { status: 401 });
            }

            licenseKeyForSession = license.license_key;

            await env.DB.prepare(`
                UPDATE licenses
                SET last_seen_name = ?, last_seen_at = ?, last_seen_client_type = ?, updated_at = CURRENT_TIMESTAMP
                WHERE license_key = ?
            `).bind(
                minecraftName,
                nowIso,
                clientType,
                license.license_key
            ).run();

            await env.DB.prepare(`
                INSERT INTO auth_logs (license_key, uuid, minecraft_name, mod_version, success, reason, ip)
                VALUES (?, ?, ?, ?, 1, 'heartbeat', ?)
            `).bind(
                license.license_key,
                uuid,
                minecraftName,
                modVersion,
                request.headers.get("CF-Connecting-IP")
            ).run();
        }

        await env.DB.prepare(`
            INSERT INTO client_sessions (uuid, minecraft_name, license_key, client_type, mod_version, ip, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid, client_type) DO UPDATE SET
                minecraft_name = excluded.minecraft_name,
                license_key = excluded.license_key,
                mod_version = excluded.mod_version,
                ip = excluded.ip,
                last_seen_at = excluded.last_seen_at,
                updated_at = CURRENT_TIMESTAMP
        `).bind(
            uuid,
            minecraftName,
            licenseKeyForSession,
            clientType,
            modVersion,
            request.headers.get("CF-Connecting-IP"),
            nowIso,
        ).run();

        await refreshPlayerOverrideName(env.DB, uuid, minecraftName);

        const selfCustomization = await resolveSelfPlayerCustomization(
            env.DB,
            uuid,
            minecraftName,
            body.selfCustomization
        );

        return Response.json({
            ok: true,
            playerOverrides: await listWireOverrides(env.DB),
            selfCustomization
        });
    } catch {
        return Response.json({ ok: false, error: "Server error" }, { status: 500 });
    }
};
