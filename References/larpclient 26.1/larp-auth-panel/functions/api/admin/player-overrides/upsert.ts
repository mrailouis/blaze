import { readCookie, verifyAdminSession } from "../../../_lib/adminAuth";
import { resolvePlayerIdentity } from "../../../_lib/playerOverrides";

interface Env {
    DB: D1Database;
    ADMIN_SESSION_SECRET: string;
}

function parseScale(value: unknown, label: string): number {
    const numeric = Number(value);

    if (!Number.isFinite(numeric)) {
        throw new Error(`${label} must be a number`);
    }

    if (numeric < 0.1 || numeric > 8) {
        throw new Error(`${label} must be between 0.1 and 8.0`);
    }

    return Math.round(numeric * 1000) / 1000;
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    const token = readCookie(request, "larp_admin_session");
    const authed = await verifyAdminSession(env.ADMIN_SESSION_SECRET, token);

    if (!authed) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    try {
        const body = await request.json() as {
            uuid?: string;
            customName?: string | null;
            scaleX?: number;
            scaleY?: number;
            scaleZ?: number;
            enabled?: boolean;
        };

        const resolved = await resolvePlayerIdentity(env.DB, body.uuid);
        if (!resolved.uuid) {
            return Response.json({ ok: false, error: "Invalid UUID" }, { status: 400 });
        }

        const customName = body.customName?.trim() || null;
        if (customName && customName.length > 128) {
            return Response.json({ ok: false, error: "Custom name is too long" }, { status: 400 });
        }

        const scaleX = parseScale(body.scaleX ?? 1, "Scale X");
        const scaleY = parseScale(body.scaleY ?? 1, "Scale Y");
        const scaleZ = parseScale(body.scaleZ ?? 1, "Scale Z");
        const enabled = body.enabled === false ? 0 : 1;
        const resolvedAt = resolved.minecraftName ? new Date().toISOString() : null;

        await env.DB.prepare(`
            INSERT INTO player_overrides (
                target_uuid,
                target_name,
                custom_name,
                scale_x,
                scale_y,
                scale_z,
                enabled,
                last_resolved_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(target_uuid) DO UPDATE SET
                target_name = COALESCE(excluded.target_name, player_overrides.target_name),
                custom_name = excluded.custom_name,
                scale_x = excluded.scale_x,
                scale_y = excluded.scale_y,
                scale_z = excluded.scale_z,
                enabled = excluded.enabled,
                last_resolved_at = COALESCE(excluded.last_resolved_at, player_overrides.last_resolved_at),
                updated_at = CURRENT_TIMESTAMP
        `).bind(
            resolved.uuid,
            resolved.minecraftName,
            customName,
            scaleX,
            scaleY,
            scaleZ,
            enabled,
            resolvedAt
        ).run();

        const row = await env.DB.prepare(`
            SELECT id, target_uuid, target_name, custom_name, scale_x, scale_y, scale_z, enabled, last_resolved_at, updated_at
            FROM player_overrides
            WHERE target_uuid = ?
            LIMIT 1
        `).bind(resolved.uuid).first<{
            id: number;
            target_uuid: string;
            target_name: string | null;
            custom_name: string | null;
            scale_x: number;
            scale_y: number;
            scale_z: number;
            enabled: number;
            last_resolved_at: string | null;
            updated_at: string;
        }>();

        return Response.json({
            ok: true,
            override: row
                ? {
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
                }
                : null
        });
    } catch (error) {
        const message = error instanceof Error ? error.message : "Save failed";
        return Response.json({ ok: false, error: message }, { status: 400 });
    }
};
