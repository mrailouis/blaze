export type PlayerOverrideRow = {
    id: number;
    target_uuid: string;
    target_name: string | null;
    custom_name: string | null;
    scale_x: number;
    scale_y: number;
    scale_z: number;
    enabled: number;
    last_resolved_at: string | null;
    created_at: string;
    updated_at: string;
};

export type PlayerOverrideWire = {
    uuid: string;
    minecraftName: string | null;
    customName: string | null;
    scaleX: number;
    scaleY: number;
    scaleZ: number;
};

export type ResolvePlayerIdentityOptions = {
    preferAuthoritative?: boolean;
};

export type SelfPlayerCustomizationUpdate = {
    customName?: string | null;
    scaleX?: number;
    scaleY?: number;
    scaleZ?: number;
    showToOthers?: boolean;
    applyChanges?: boolean;
};

export type SelfPlayerCustomizationState = {
    eligible: boolean;
    authenticated: boolean;
    minecraftName: string | null;
    customName: string | null;
    scaleX: number;
    scaleY: number;
    scaleZ: number;
    showToOthers: boolean;
};

const UUID_CHARS = /^[0-9a-f]{32}$/i;

export function normalizePlayerUuid(value: string | null | undefined): string | null {
    const compact = value?.trim().replace(/-/g, "").toLowerCase() ?? "";
    if (!UUID_CHARS.test(compact)) {
        return null;
    }

    return [
        compact.slice(0, 8),
        compact.slice(8, 12),
        compact.slice(12, 16),
        compact.slice(16, 20),
        compact.slice(20)
    ].join("-");
}

function compactUuid(uuid: string): string {
    return uuid.replace(/-/g, "").toLowerCase();
}

function cleanName(value: string | null | undefined): string | null {
    return value?.trim() || null;
}

function namesMatch(left: string | null | undefined, right: string | null | undefined): boolean {
    const cleanedLeft = cleanName(left);
    const cleanedRight = cleanName(right);
    return !!cleanedLeft && !!cleanedRight && cleanedLeft.localeCompare(cleanedRight, undefined, { sensitivity: "accent" }) === 0;
}

export function parsePlayerOverrideScale(value: unknown, label: string): number {
    const numeric = Number(value);

    if (!Number.isFinite(numeric)) {
        throw new Error(`${label} must be a number`);
    }

    if (numeric < 0.1 || numeric > 8) {
        throw new Error(`${label} must be between 0.1 and 8.0`);
    }

    return Math.round(numeric * 1000) / 1000;
}

async function resolveNameFromSessions(db: D1Database, uuid: string): Promise<string | null> {
    const session = await db.prepare(`
        SELECT minecraft_name
        FROM client_sessions
        WHERE uuid = ? AND minecraft_name IS NOT NULL AND TRIM(minecraft_name) != ''
        ORDER BY datetime(last_seen_at) DESC
        LIMIT 1
    `).bind(uuid).first<{ minecraft_name: string | null }>();

    if (session?.minecraft_name?.trim()) {
        return session.minecraft_name.trim();
    }

    const license = await db.prepare(`
        SELECT last_seen_name
        FROM licenses
        WHERE bound_uuid = ? AND last_seen_name IS NOT NULL AND TRIM(last_seen_name) != ''
        ORDER BY datetime(last_seen_at) DESC
        LIMIT 1
    `).bind(uuid).first<{ last_seen_name: string | null }>();

    return license?.last_seen_name?.trim() || null;
}

async function resolveNameFromMojang(uuid: string): Promise<string | null> {
    try {
        const res = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${compactUuid(uuid)}`, {
            headers: {
                "Accept": "application/json"
            }
        });

        if (!res.ok) {
            return null;
        }

        const data = await res.json() as { name?: string };
        return data.name?.trim() || null;
    } catch {
        return null;
    }
}

async function getPlayerOverrideByUuid(db: D1Database, uuid: string): Promise<PlayerOverrideRow | null> {
    return db.prepare(`
        SELECT id, target_uuid, target_name, custom_name, scale_x, scale_y, scale_z, enabled, last_resolved_at, created_at, updated_at
        FROM player_overrides
        WHERE LOWER(REPLACE(target_uuid, '-', '')) = ?
        LIMIT 1
    `).bind(compactUuid(uuid)).first<PlayerOverrideRow>();
}

async function getPlayerOverrideByName(db: D1Database, minecraftName: string): Promise<PlayerOverrideRow | null> {
    const cleanedName = cleanName(minecraftName);
    if (!cleanedName) {
        return null;
    }

    return db.prepare(`
        SELECT id, target_uuid, target_name, custom_name, scale_x, scale_y, scale_z, enabled, last_resolved_at, created_at, updated_at
        FROM player_overrides
        WHERE LOWER(TRIM(target_name)) = LOWER(?)
        ORDER BY datetime(updated_at) DESC, id DESC
        LIMIT 1
    `).bind(cleanedName).first<PlayerOverrideRow>();
}

export async function resolvePlayerIdentity(
    db: D1Database,
    rawUuid: string | null | undefined,
    options: ResolvePlayerIdentityOptions = {}
): Promise<{ uuid: string | null; minecraftName: string | null; source: string }> {
    const uuid = normalizePlayerUuid(rawUuid);
    if (!uuid) {
        return { uuid: null, minecraftName: null, source: "invalid" };
    }

    if (options.preferAuthoritative) {
        const mojangName = await resolveNameFromMojang(uuid);
        if (mojangName) {
            return { uuid, minecraftName: mojangName, source: "mojang" };
        }
    }

    const sessionName = await resolveNameFromSessions(db, uuid);
    if (sessionName) {
        return { uuid, minecraftName: sessionName, source: "session" };
    }

    const mojangName = await resolveNameFromMojang(uuid);
    if (mojangName) {
        return { uuid, minecraftName: mojangName, source: "mojang" };
    }

    return { uuid, minecraftName: null, source: "unresolved" };
}

export async function refreshPlayerOverrideName(
    db: D1Database,
    rawUuid: string | null | undefined,
    minecraftName: string | null | undefined
): Promise<void> {
    const uuid = normalizePlayerUuid(rawUuid);
    const cleanedName = cleanName(minecraftName);

    if (!uuid || !cleanedName) {
        return;
    }

    await db.prepare(`
        UPDATE player_overrides
        SET target_name = ?,
            last_resolved_at = ?
        WHERE LOWER(REPLACE(target_uuid, '-', '')) = ?
    `).bind(cleanedName, new Date().toISOString(), compactUuid(uuid)).run();
}

export async function resolveSelfPlayerCustomization(
    db: D1Database,
    rawUuid: string | null | undefined,
    reportedMinecraftName: string | null | undefined,
    requestedUpdate?: SelfPlayerCustomizationUpdate | null
): Promise<SelfPlayerCustomizationState> {
    const uuid = normalizePlayerUuid(rawUuid);
    if (!uuid) {
        return {
            eligible: false,
            authenticated: false,
            minecraftName: null,
            customName: null,
            scaleX: 1,
            scaleY: 1,
            scaleZ: 1,
            showToOthers: false
        };
    }

    let row = await getPlayerOverrideByUuid(db, uuid);
    const identity = await resolvePlayerIdentity(db, uuid, { preferAuthoritative: !!row });
    const effectiveName = cleanName(identity.minecraftName) || cleanName(reportedMinecraftName) || cleanName(row?.target_name);

    if (!row && effectiveName) {
        const nameMatchedRow = await getPlayerOverrideByName(db, effectiveName);
        if (nameMatchedRow) {
            await db.prepare(`
                UPDATE player_overrides
                SET target_uuid = ?,
                    target_name = COALESCE(?, target_name),
                    last_resolved_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            `).bind(
                uuid,
                effectiveName,
                new Date().toISOString(),
                nameMatchedRow.id
            ).run();

            row = await getPlayerOverrideByUuid(db, uuid);
        }
    }

    if (effectiveName) {
        await refreshPlayerOverrideName(db, uuid, effectiveName);
    }

    if (!row) {
        return {
            eligible: false,
            authenticated: false,
            minecraftName: effectiveName,
            customName: null,
            scaleX: 1,
            scaleY: 1,
            scaleZ: 1,
            showToOthers: false
        };
    }

    const authenticated = namesMatch(reportedMinecraftName, effectiveName);

    if (requestedUpdate?.applyChanges === true && authenticated) {
        const customName = cleanName(requestedUpdate.customName);
        if (customName && customName.length > 128) {
            throw new Error("Custom name is too long");
        }

        const scaleX = parsePlayerOverrideScale(requestedUpdate.scaleX ?? row.scale_x ?? 1, "Scale X");
        const scaleY = parsePlayerOverrideScale(requestedUpdate.scaleY ?? row.scale_y ?? 1, "Scale Y");
        const scaleZ = parsePlayerOverrideScale(requestedUpdate.scaleZ ?? row.scale_z ?? 1, "Scale Z");
        const enabled = requestedUpdate.showToOthers === true ? 1 : 0;
        const resolvedAt = effectiveName ? new Date().toISOString() : row.last_resolved_at;

        await db.prepare(`
            UPDATE player_overrides
            SET target_name = COALESCE(?, target_name),
                custom_name = ?,
                scale_x = ?,
                scale_y = ?,
                scale_z = ?,
                enabled = ?,
                last_resolved_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE target_uuid = ?
        `).bind(
            effectiveName,
            customName,
            scaleX,
            scaleY,
            scaleZ,
            enabled,
            resolvedAt,
            uuid
        ).run();
    }

    row = await getPlayerOverrideByUuid(db, uuid);

    return {
        eligible: true,
        authenticated,
        minecraftName: effectiveName || cleanName(row?.target_name),
        customName: cleanName(row?.custom_name),
        scaleX: Number(row?.scale_x ?? 1),
        scaleY: Number(row?.scale_y ?? 1),
        scaleZ: Number(row?.scale_z ?? 1),
        showToOthers: row?.enabled === 1
    };
}

export async function listPlayerOverrides(db: D1Database, enabledOnly = false): Promise<PlayerOverrideRow[]> {
    const query = enabledOnly
        ? `
            SELECT id, target_uuid, target_name, custom_name, scale_x, scale_y, scale_z, enabled, last_resolved_at, created_at, updated_at
            FROM player_overrides
            WHERE enabled = 1
            ORDER BY datetime(updated_at) DESC, id DESC
        `
        : `
            SELECT id, target_uuid, target_name, custom_name, scale_x, scale_y, scale_z, enabled, last_resolved_at, created_at, updated_at
            FROM player_overrides
            ORDER BY datetime(updated_at) DESC, id DESC
        `;

    const result = await db.prepare(query).all<PlayerOverrideRow>();
    return result.results ?? [];
}

export function toWireOverride(row: PlayerOverrideRow): PlayerOverrideWire {
    return {
        uuid: row.target_uuid,
        minecraftName: row.target_name,
        customName: row.custom_name,
        scaleX: Number(row.scale_x ?? 1),
        scaleY: Number(row.scale_y ?? 1),
        scaleZ: Number(row.scale_z ?? 1)
    };
}

export async function listWireOverrides(db: D1Database): Promise<PlayerOverrideWire[]> {
    const rows = await listPlayerOverrides(db, true);
    return rows
        .filter((row) => {
            const hasName = !!row.custom_name?.trim();
            const hasScale = Number(row.scale_x) !== 1 || Number(row.scale_y) !== 1 || Number(row.scale_z) !== 1;
            return hasName || hasScale;
        })
        .map(toWireOverride);
}
