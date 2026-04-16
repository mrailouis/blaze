CREATE INDEX IF NOT EXISTS idx_licenses_product_tier_status
    ON licenses (product_tier, status);

CREATE TABLE IF NOT EXISTS client_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    minecraft_name TEXT,
    license_key TEXT,
    client_type TEXT NOT NULL,
    mod_version TEXT,
    ip TEXT,
    last_seen_at TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(uuid, client_type)
);

CREATE INDEX IF NOT EXISTS idx_client_sessions_last_seen_at
    ON client_sessions (last_seen_at);

CREATE INDEX IF NOT EXISTS idx_client_sessions_client_type_last_seen_at
    ON client_sessions (client_type, last_seen_at);

CREATE TABLE IF NOT EXISTS site_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS site_documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    excerpt TEXT,
    kind TEXT NOT NULL DEFAULT 'wiki',
    audience TEXT NOT NULL DEFAULT 'public',
    category TEXT,
    subcategory TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    body_markdown TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_site_documents_kind_audience_sort
    ON site_documents (kind, audience, sort_order, updated_at);

CREATE INDEX IF NOT EXISTS idx_site_documents_kind_audience_category_sort
    ON site_documents (kind, audience, category, subcategory, sort_order, updated_at);

CREATE TABLE IF NOT EXISTS site_videos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    description TEXT,
    audience TEXT NOT NULL DEFAULT 'public',
    sort_order INTEGER NOT NULL DEFAULT 0,
    r2_key TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_site_videos_audience_sort
    ON site_videos (audience, sort_order, updated_at);

INSERT OR IGNORE INTO site_settings (key, value)
VALUES
    ('discord_url', 'https://discord.gg/replace-me'),
    ('support_url', 'https://larpclient.pages.dev/login'),
    ('download_url', 'https://larpclient.pages.dev/wiki'),
    ('source_code_url', 'https://github.com/replace-me/larpclient-public');

INSERT OR IGNORE INTO site_documents (slug, title, excerpt, kind, audience, sort_order, body_markdown)
VALUES
    (
        'public-home',
        'Larp',
        'Legit utility modules, cleaner visuals, and shared status tracking.',
        'page',
        'public',
        0,
        '# Legit Features

- Clean scoreboard and HUD tooling
- Item rarity, routing, and dungeon helpers
- Shared online heartbeat every 10 seconds

## Links

- [Discord](https://discord.gg/replace-me)
- [Public Wiki](/wiki)
- [Addon Login](/login)
'
    ),
    (
        'addon-home',
        'Larp Addon',
        'Licensed addon-only modules, private wiki pages, and private feature videos.',
        'page',
        'addon',
        0,
        '# Addon Features

- Licensed-only modules and routes
- Private wiki pages
- Private feature videos

## Notes

Only addon licenses can access this page.
'
    ),
    (
        'getting-started',
        'Getting Started',
        'Install the legit mod and use the public wiki to onboard users quickly.',
        'wiki',
        'public',
        10,
        '# Getting Started

Use this page for install steps, HUD setup, and public documentation.
'
    ),
    (
        'addon-quickstart',
        'Addon Quickstart',
        'Private setup notes for licensed addon users.',
        'wiki',
        'addon',
        10,
        '# Addon Quickstart

Use this page for licensed setup steps, private routes, and addon notes.
'
    );
