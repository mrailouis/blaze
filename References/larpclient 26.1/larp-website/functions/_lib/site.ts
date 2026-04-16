import { validatePortalLicense } from "./licenses";
import { getSessionFromRequest, type SiteSessionPayload } from "./session";

export type SiteDocumentRow = {
    id: number;
    slug: string;
    title: string;
    excerpt: string | null;
    kind: "page" | "wiki";
    audience: "public" | "addon";
    category: string | null;
    subcategory: string | null;
    sort_order: number;
    body_markdown: string;
    updated_at: string;
};

export type SiteVideoRow = {
    id: number;
    slug: string;
    title: string;
    description: string | null;
    audience: "public" | "addon";
    sort_order: number;
    r2_key: string;
    mime_type: string;
    size_bytes: number;
    updated_at: string;
};

type SettingRow = {
    key: string;
    value: string;
};

type OnlineCountRow = {
    client_type: string;
    count: number;
};

export interface SiteEnv {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
}

export function slugify(value: string): string {
    return value
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 64);
}

export function normalizeAudience(value: unknown): "public" | "addon" {
    return value === "addon" ? "addon" : "public";
}

export function normalizeKind(value: unknown): "page" | "wiki" {
    return value === "page" ? "page" : "wiki";
}

export function normalizeOptionalText(value: unknown): string | null {
    if (typeof value !== "string") {
        return null;
    }

    const normalized = value.trim();
    return normalized ? normalized : null;
}

export function hasAddonAccess(session: SiteSessionPayload | null): boolean {
    return session?.role === "admin" || (session?.role === "license" && session.productTier === "addon");
}

export function canAccessAudience(session: SiteSessionPayload | null, audience: "public" | "addon"): boolean {
    return audience === "public" || hasAddonAccess(session);
}

export async function getSiteSession(request: Request, env: SiteEnv): Promise<SiteSessionPayload | null> {
    const session = await getSessionFromRequest(request, env.SITE_SESSION_SECRET);

    if (!session) {
        return null;
    }

    if (session.role === "admin") {
        return {
            ...session,
            username: session.username || "mrai"
        };
    }

    if (!session.licenseKey || !session.discordName) {
        return null;
    }

    const license = await validatePortalLicense(env.DB, session.licenseKey, session.discordName);
    if (!license.ok) {
        return null;
    }

    if (session.portalPasswordSetAt !== license.license.portal_password_set_at) {
        return null;
    }

    return {
        ...session,
        licenseKey: license.license.license_key,
        productTier: license.license.product_tier,
        discordName: license.license.discord_name ?? session.discordName
    };
}

export async function requireAdmin(request: Request, env: SiteEnv): Promise<SiteSessionPayload | null> {
    const session = await getSiteSession(request, env);
    return session?.role === "admin" ? session : null;
}

export async function fetchSettings(env: Pick<SiteEnv, "DB">): Promise<Record<string, string>> {
    const result = await env.DB.prepare(`
        SELECT key, value
        FROM site_settings
        ORDER BY key ASC
    `).all<SettingRow>();

    const settings: Record<string, string> = {
        discord_url: "https://discord.gg/replace-me",
        support_url: "/login?mode=addon",
        download_url: "/wiki",
        source_code_url: "https://github.com/mrailouis/larpclient-public"
    };

    for (const row of result.results ?? []) {
        settings[row.key] = row.value;
    }

    return settings;
}

export async function fetchDocuments(env: Pick<SiteEnv, "DB">, session: SiteSessionPayload | null): Promise<SiteDocumentRow[]> {
    const result = await env.DB.prepare(`
        SELECT id, slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown, updated_at
        FROM site_documents
        ORDER BY kind ASC, audience ASC, COALESCE(category, '') ASC, COALESCE(subcategory, '') ASC, sort_order ASC, title ASC
    `).all<SiteDocumentRow>();

    return (result.results ?? []).filter((row) => canAccessAudience(session, row.audience));
}

export async function fetchAllDocuments(env: Pick<SiteEnv, "DB">): Promise<SiteDocumentRow[]> {
    const result = await env.DB.prepare(`
        SELECT id, slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown, updated_at
        FROM site_documents
        ORDER BY kind ASC, audience ASC, COALESCE(category, '') ASC, COALESCE(subcategory, '') ASC, sort_order ASC, title ASC
    `).all<SiteDocumentRow>();

    return result.results ?? [];
}

export async function fetchVideos(env: Pick<SiteEnv, "DB">, session: SiteSessionPayload | null) {
    const result = await env.DB.prepare(`
        SELECT id, slug, title, description, audience, sort_order, r2_key, mime_type, size_bytes, updated_at
        FROM site_videos
        ORDER BY audience ASC, sort_order ASC, title ASC
    `).all<SiteVideoRow>();

    return (result.results ?? [])
        .filter((row) => canAccessAudience(session, row.audience))
        .map((row) => ({
            ...row,
            streamUrl: `/api/media/stream?id=${row.id}`
        }));
}

export async function fetchAllVideos(env: Pick<SiteEnv, "DB">): Promise<SiteVideoRow[]> {
    const result = await env.DB.prepare(`
        SELECT id, slug, title, description, audience, sort_order, r2_key, mime_type, size_bytes, updated_at
        FROM site_videos
        ORDER BY audience ASC, sort_order ASC, title ASC
    `).all<SiteVideoRow>();

    return result.results ?? [];
}

export async function fetchOnlineTotals(env: Pick<SiteEnv, "DB">) {
    const result = await env.DB.prepare(`
        SELECT client_type, COUNT(*) AS count
        FROM client_sessions
        WHERE datetime(last_seen_at) >= datetime('now', '-45 seconds')
        GROUP BY client_type
    `).all<OnlineCountRow>();

    const rows = result.results ?? [];
    const mod = rows.find((row) => row.client_type === "larp-mod")?.count ?? 0;
    const addon = rows.find((row) => row.client_type === "larp-addon")?.count ?? 0;

    return {
        total: mod + addon,
        mod,
        addon
    };
}
