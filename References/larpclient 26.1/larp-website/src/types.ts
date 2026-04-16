export type SessionInfo = {
    role: "admin" | "license";
    productTier: string | null;
    discordName: string | null;
    username: string | null;
} | null;

export type OnlineTotals = {
    total: number;
    mod: number;
    addon: number;
};

export type SiteDocument = {
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

export type SiteVideo = {
    id: number;
    slug: string;
    title: string;
    description: string | null;
    audience: "public" | "addon";
    sort_order: number;
    mime_type: string;
    size_bytes: number;
    updated_at: string;
    streamUrl: string;
};

export type BootstrapData = {
    ok: true;
    session: SessionInfo;
    settings: Record<string, string>;
    onlineTotals: OnlineTotals;
    publicHome: SiteDocument | null;
    addonHome: SiteDocument | null;
    publicWiki: SiteDocument[];
    addonWiki: SiteDocument[];
    publicVideos: SiteVideo[];
    addonVideos: SiteVideo[];
};

export type DocumentsResponse = {
    ok: true;
    documents: SiteDocument[];
};

export type SettingsResponse = {
    ok: true;
    settings: Record<string, string>;
};

export type VideosResponse = {
    ok: true;
    videos: SiteVideo[];
};
