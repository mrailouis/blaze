CREATE TABLE IF NOT EXISTS licenses (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        license_key TEXT NOT NULL UNIQUE,
                                        discord_name TEXT NOT NULL,
                                        bound_uuid TEXT,
                                        portal_password_hash TEXT,
                                        portal_password_salt TEXT,
                                        portal_password_set_at TEXT,
                                        last_seen_name TEXT,
                                        last_seen_at TEXT,
                                        last_seen_client_type TEXT,
                                        product_tier TEXT NOT NULL DEFAULT 'addon',
                                        status TEXT NOT NULL DEFAULT 'active',
                                        expires_at TEXT NOT NULL,
                                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_logs (
                                         id INTEGER PRIMARY KEY AUTOINCREMENT,
                                         license_key TEXT,
                                         uuid TEXT,
                                         minecraft_name TEXT,
                                         mod_version TEXT,
                                         success INTEGER NOT NULL,
                                         reason TEXT,
                                         ip TEXT,
                                         created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_licenses_product_tier_status
    ON licenses (product_tier, status);

CREATE INDEX IF NOT EXISTS idx_licenses_discord_name
    ON licenses (discord_name);

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

CREATE TABLE IF NOT EXISTS player_overrides (
                                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                                target_uuid TEXT NOT NULL UNIQUE,
                                                target_name TEXT,
                                                custom_name TEXT,
                                                scale_x REAL NOT NULL DEFAULT 1,
                                                scale_y REAL NOT NULL DEFAULT 1,
                                                scale_z REAL NOT NULL DEFAULT 1,
                                                enabled INTEGER NOT NULL DEFAULT 1,
                                                last_resolved_at TEXT,
                                                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_player_overrides_enabled_updated_at
    ON player_overrides (enabled, updated_at);

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

INSERT OR IGNORE INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES
    (
        'public-home',
        'Larp',
        'Legit utility modules, cleaner visuals, and shared status tracking.',
        'page',
        'public',
        NULL,
        NULL,
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
        NULL,
        NULL,
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
        'public-wiki-guide',
        'How To Use This Wiki',
        'Guide for editing and extending the legit wiki structure.',
        'wiki',
        'public',
        'Misc',
        NULL,
        5,
        '# How To Use This Wiki

This is the legit wiki. It mirrors the in-game sidebar so the website structure matches the click GUI.

## How the framework is organised

- Sidebar categories follow the game order: Skyblock, Dungeons, Kuudra, Misc.
- Floor 7 pages live under `Dungeons / Floor 7`.
- Each page lists the modules currently assigned to that sidebar entry.
- Empty sections already include a placeholder block for future modules.

## How to fill the placeholders

1. Start with the section overview at the top of the page.
2. For each module, replace the italic placeholder text with setup steps, settings, caveats, and examples.
3. Add screenshots, clips, routes, and command examples where they make sense.
4. If a module changes category in the client, move the wiki page content to match the new sidebar location.

## Editing notes

- Keep legit content inside `/wiki` only.
- Use the module name exactly as it appears in-game.
- Keep one page per sidebar section instead of splitting a single section across many unrelated pages.

## Checklist

- Add a short summary for the section.
- Document activation and setup.
- List the important settings.
- Add caveats, restrictions, and route-specific notes.
- Add media links or screenshots if they help.
'
    ),
    (
        'public-skyblock-general',
        'General',
        'Framework page for Skyblock / General.',
        'wiki',
        'public',
        'Skyblock',
        NULL,
        10,
        '# Skyblock / General

Use this page to document the legit modules in `Skyblock / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Server Lag Detection

**Current summary:** Tracks time lost to lag in Kuudra and Dungeons.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Auto Clicker

**Current summary:** Configurable left-click autoclicker with CPS and humanisation.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Mute ID Hider

**Current summary:** Hides Hypixel mute blocks and replaces them with a clean message.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-skyblock-golems',
        'Golems',
        'Framework page for Skyblock / Golems.',
        'wiki',
        'public',
        'Skyblock',
        NULL,
        20,
        '# Skyblock / Golems

Use this page to document the legit modules in `Skyblock / Golems`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-dungeons-general',
        'General',
        'Framework page for Dungeons / General.',
        'wiki',
        'public',
        'Dungeons',
        NULL,
        10,
        '# Dungeons / General

Use this page to document the legit modules in `Dungeons / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### True Splits

**Current summary:** Over-engineered M7 dungeon splits with real time, tick time and breakdown HUDs.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Last Breath Utils

**Current summary:** Tracks Last Breath arrows and shows how many hit entities.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-dungeons-floor-7-general',
        'General',
        'Framework page for Dungeons / Floor 7 / General.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        10,
        '# Dungeons / Floor 7 / General

Use this page to document the legit modules in `Dungeons / Floor 7 / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Archer Utils

**Current summary:** Spray and death bow helpers for Floor 7.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Wither ESP

**Current summary:** Renders configurable boxes around withers.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Positional Messages

**Current summary:** Sends saved commands once when you walk into their configured rings.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-dungeons-floor-7-predev',
        'Predev',
        'Framework page for Dungeons / Floor 7 / Predev.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        20,
        '# Dungeons / Floor 7 / Predev

Use this page to document the legit modules in `Dungeons / Floor 7 / Predev`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-dungeons-floor-7-phase-1',
        'Phase 1',
        'Framework page for Dungeons / Floor 7 / Phase 1.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        30,
        '# Dungeons / Floor 7 / Phase 1

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 1`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Maxor HP% HUD

**Current summary:** Shows Maxor boss bar HP as a movable HUD.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-dungeons-floor-7-phase-2',
        'Phase 2',
        'Framework page for Dungeons / Floor 7 / Phase 2.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        40,
        '# Dungeons / Floor 7 / Phase 2

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 2`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Gyro Waypoints

**Current summary:** Renders purple gyro waypoints for Floor 7 P2.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-dungeons-floor-7-phase-3',
        'Phase 3',
        'Framework page for Dungeons / Floor 7 / Phase 3.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        50,
        '# Dungeons / Floor 7 / Phase 3

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 3`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Term GUI

**Current summary:** Replaces Floor 7 terminals with a compact custom solver GUI.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-dungeons-floor-7-phase-4',
        'Phase 4',
        'Framework page for Dungeons / Floor 7 / Phase 4.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        60,
        '# Dungeons / Floor 7 / Phase 4

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 4`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-dungeons-floor-7-phase-5',
        'Phase 5',
        'Framework page for Dungeons / Floor 7 / Phase 5.',
        'wiki',
        'public',
        'Dungeons',
        'Floor 7',
        70,
        '# Dungeons / Floor 7 / Phase 5

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 5`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-kuudra-general',
        'General',
        'Framework page for Kuudra / General.',
        'wiki',
        'public',
        'Kuudra',
        NULL,
        10,
        '# Kuudra / General

Use this page to document the legit modules in `Kuudra / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-kuudra-phase-1',
        'Phase 1',
        'Framework page for Kuudra / Phase 1.',
        'wiki',
        'public',
        'Kuudra',
        NULL,
        20,
        '# Kuudra / Phase 1

Use this page to document the legit modules in `Kuudra / Phase 1`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Kuudra Waypoints

**Current summary:** IQ-like Kuudra pearl helper with dynamic sky/flat calculations.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-kuudra-phase-2',
        'Phase 2',
        'Framework page for Kuudra / Phase 2.',
        'wiki',
        'public',
        'Kuudra',
        NULL,
        30,
        '# Kuudra / Phase 2

Use this page to document the legit modules in `Kuudra / Phase 2`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-kuudra-phase-3',
        'Phase 3',
        'Framework page for Kuudra / Phase 3.',
        'wiki',
        'public',
        'Kuudra',
        NULL,
        40,
        '# Kuudra / Phase 3

Use this page to document the legit modules in `Kuudra / Phase 3`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-kuudra-phase-4',
        'Phase 4',
        'Framework page for Kuudra / Phase 4.',
        'wiki',
        'public',
        'Kuudra',
        NULL,
        50,
        '# Kuudra / Phase 4

Use this page to document the legit modules in `Kuudra / Phase 4`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'public-misc-ui',
        'UI',
        'Framework page for Misc / UI.',
        'wiki',
        'public',
        'Misc',
        NULL,
        10,
        '# Misc / UI

Use this page to document the legit modules in `Misc / UI`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Clean Scoreboard

**Current summary:** Renders a custom cleaned scoreboard with a footer.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Module List

**Current summary:** Shows enabled modules in a sliding list on the top right.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Item Rarity

**Current summary:** Draws rarity-colored backgrounds behind item icons in inventories and the hotbar.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'public-misc-other',
        'Other',
        'Framework page for Misc / Other.',
        'wiki',
        'public',
        'Misc',
        NULL,
        20,
        '# Misc / Other

Use this page to document the legit modules in `Misc / Other`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Discord Rich Presence

**Current summary:** Shows your LarpClient activity on Discord.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Heads Scale

**Current summary:** Scales head items in GUI renders.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-wiki-guide',
        'How To Use This Wiki',
        'Guide for editing and extending the addon-only wiki structure.',
        'wiki',
        'addon',
        'Misc',
        NULL,
        5,
        '# How To Use This Wiki

This is the addon-only wiki. It mirrors the in-game sidebar so the website structure matches the click GUI.

## How the framework is organised

- Sidebar categories follow the game order: Skyblock, Dungeons, Kuudra, Misc.
- Floor 7 pages live under `Dungeons / Floor 7`.
- Each page lists the modules currently assigned to that sidebar entry.
- Empty sections already include a placeholder block for future modules.

## How to fill the placeholders

1. Start with the section overview at the top of the page.
2. For each module, replace the italic placeholder text with setup steps, settings, caveats, and examples.
3. Add screenshots, clips, routes, and command examples where they make sense.
4. If a module changes category in the client, move the wiki page content to match the new sidebar location.

## Editing notes

- Keep addon-only content inside `/portal/wiki` only.
- Use the module name exactly as it appears in-game.
- Keep one page per sidebar section instead of splitting a single section across many unrelated pages.

## Checklist

- Add a short summary for the section.
- Document activation and setup.
- List the important settings.
- Add caveats, restrictions, and route-specific notes.
- Add media links or screenshots if they help.
'
    ),
    (
        'addon-skyblock-general',
        'General',
        'Framework page for Skyblock / General.',
        'wiki',
        'addon',
        'Skyblock',
        NULL,
        10,
        '# Skyblock / General

Use this page to document the addon-only modules in `Skyblock / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Blink

**Current summary:** Records blink inputs and replays them through buffered packets.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Cancel Interact

**Current summary:** Prevents block interaction so right click uses the held item instead.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### CGY Wardrobe

**Current summary:** Swaps wardrobe slots silently without opening the GUI.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Simple Autoroutes

**Current summary:** Etherwarp routes with await click and raytrace gating.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-skyblock-golems',
        'Golems',
        'Framework page for Skyblock / Golems.',
        'wiki',
        'addon',
        'Skyblock',
        NULL,
        20,
        '# Skyblock / Golems

Use this page to document the addon-only modules in `Skyblock / Golems`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-dungeons-general',
        'General',
        'Framework page for Dungeons / General.',
        'wiki',
        'addon',
        'Dungeons',
        NULL,
        10,
        '# Dungeons / General

Use this page to document the addon-only modules in `Dungeons / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Dungeonbreaker Nuker

**Current summary:** Captures dungeon blocks and breaks them in range using a hotbar item named dungeonbreaker. Auto 3x3 can target the fixed arrow stack area by default.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Velocity Buffer

**Current summary:** Buffers incoming velocity packets until you pop or flush them.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-dungeons-floor-7-general',
        'General',
        'Framework page for Dungeons / Floor 7 / General.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        10,
        '# Dungeons / Floor 7 / General

Use this page to document the addon-only modules in `Dungeons / Floor 7 / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-dungeons-floor-7-predev',
        'Predev',
        'Framework page for Dungeons / Floor 7 / Predev.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        20,
        '# Dungeons / Floor 7 / Predev

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Predev`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Lever Aura

**Current summary:** Flicks nearby levers outside the lights device.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Lights Device

**Current summary:** Predev lights helper.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Soulsand Aura

**Current summary:** Marks valid placement spots in predev and automatically places soulsand from your hotbar when you get into range.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-dungeons-floor-7-phase-1',
        'Phase 1',
        'Framework page for Dungeons / Floor 7 / Phase 1.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        30,
        '# Dungeons / Floor 7 / Phase 1

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Phase 1`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-dungeons-floor-7-phase-2',
        'Phase 2',
        'Framework page for Dungeons / Floor 7 / Phase 2.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        40,
        '# Dungeons / Floor 7 / Phase 2

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Phase 2`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-dungeons-floor-7-phase-3',
        'Phase 3',
        'Framework page for Dungeons / Floor 7 / Phase 3.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        50,
        '# Dungeons / Floor 7 / Phase 3

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Phase 3`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Auto SS

**Current summary:** Ports Simon Shut Up AutoSS for Floor 7 P3 Simon Says.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Auto P3

**Current summary:** Floor 7 P3 ring editor with thin circular rings and dot commands.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-dungeons-floor-7-phase-4',
        'Phase 4',
        'Framework page for Dungeons / Floor 7 / Phase 4.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        60,
        '# Dungeons / Floor 7 / Phase 4

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Phase 4`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-dungeons-floor-7-phase-5',
        'Phase 5',
        'Framework page for Dungeons / Floor 7 / Phase 5.',
        'wiki',
        'addon',
        'Dungeons',
        'Floor 7',
        70,
        '# Dungeons / Floor 7 / Phase 5

Use this page to document the addon-only modules in `Dungeons / Floor 7 / Phase 5`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Arrow Stack Waypoints

**Current summary:** Switches the active Floor 7 P5 arrow stack waypoint based on which color ring you enter.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Auto Cow Hat

**Current summary:** Silently equips Cow Hat when you enter configured zones.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Debuff Snap

**Current summary:** Snaps your view straight up every time you enter a configured ring.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-kuudra-general',
        'General',
        'Framework page for Kuudra / General.',
        'wiki',
        'addon',
        'Kuudra',
        NULL,
        10,
        '# Kuudra / General

Use this page to document the addon-only modules in `Kuudra / General`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Left Click Shop

**Current summary:** Use left click with the Open Shop item to send a right click while keeping swing.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-kuudra-phase-1',
        'Phase 1',
        'Framework page for Kuudra / Phase 1.',
        'wiki',
        'addon',
        'Kuudra',
        NULL,
        20,
        '# Kuudra / Phase 1

Use this page to document the addon-only modules in `Kuudra / Phase 1`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Auto Pearls

**Current summary:** Automatically throws Kuudra waypoint pearls at the timed angles.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Auto Route to Pre Locations

**Current summary:** Renders and plays saved preroutes.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Crate Aura

**Current summary:** Interacts with Kuudra supply crate zombies using C2S interact packets.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._

### Pearl Prediction

**Current summary:** Predicts pearl landing blocks and highlights pile proximity.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-kuudra-phase-2',
        'Phase 2',
        'Framework page for Kuudra / Phase 2.',
        'wiki',
        'addon',
        'Kuudra',
        NULL,
        30,
        '# Kuudra / Phase 2

Use this page to document the addon-only modules in `Kuudra / Phase 2`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-kuudra-phase-3',
        'Phase 3',
        'Framework page for Kuudra / Phase 3.',
        'wiki',
        'addon',
        'Kuudra',
        NULL,
        40,
        '# Kuudra / Phase 3

Use this page to document the addon-only modules in `Kuudra / Phase 3`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Skip Waypoint

**Current summary:** Renders a configurable skip waypoint box that can be depthless.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-kuudra-phase-4',
        'Phase 4',
        'Framework page for Kuudra / Phase 4.',
        'wiki',
        'addon',
        'Kuudra',
        NULL,
        50,
        '# Kuudra / Phase 4

Use this page to document the addon-only modules in `Kuudra / Phase 4`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Backbone Lock

**Current summary:** Locks your hotbar after a backbone bonemerang hit and can auto swap to your rend weapon.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    ),
    (
        'addon-misc-ui',
        'UI',
        'Framework page for Misc / UI.',
        'wiki',
        'addon',
        'Misc',
        NULL,
        10,
        '# Misc / UI

Use this page to document the addon-only modules in `Misc / UI`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

_No modules are mapped to this sidebar entry yet._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add limitations, routes, and examples here._
'
    ),
    (
        'addon-misc-other',
        'Other',
        'Framework page for Misc / Other.',
        'wiki',
        'addon',
        'Misc',
        NULL,
        20,
        '# Misc / Other

Use this page to document the addon-only modules in `Misc / Other`.

## Section Notes

- Add a short overview for this section.
- Add setup steps, screenshots, routes, and edge cases.
- Keep the module names aligned with the in-game sidebar.

## Modules

### Trail

**Current summary:** Renders a trail under your feet.

**How to use it:** _Add activation, setup, and workflow details here._

**Key settings:** _List the important toggles, sliders, and modes here._

**Notes / caveats:** _Add route-specific notes, restrictions, and examples here._

**Media / examples:** _Add screenshots, clips, or example scenarios here._
'
    );

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-wiki-guide',
    'How To Use This Wiki',
    'Guide for editing and extending the legit wiki structure.',
    'wiki',
    'public',
    'Misc',
    NULL,
    5,
    '# How To Use This Wiki

This is the legit wiki. It only documents the legit feature set and mirrors the in-game sidebar.

## Rules

- Use `/wiki` for legit pages only.
- Use `/portal/wiki` for addon-only pages and addon-only automation settings.
- Keep page titles and module names aligned with the in-game sidebar.
- If a legit feature gains extra addon automation, document the base behavior here and the extra automation in the addon wiki.

## Editing checklist

1. Add a short section overview.
2. Replace each placeholder with setup, settings, and caveats.
3. Add screenshots, routes, or clips where they help.
4. Move content if a module moves to a different sidebar category in-game.
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-skyblock-general',
    'General',
    'Framework page for Skyblock / General.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    10,
    '# Skyblock / General

Use this page to document the legit modules in `Skyblock / General`.

## Modules

### Server Lag Detection

**Current summary:** Tracks lag spikes and stalled states during gameplay.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Mute ID Hider

**Current summary:** Hides mute IDs and replaces them with a cleaner message.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-skyblock-golems',
    'Golems',
    'Framework page for Skyblock / Golems.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    20,
    '# Skyblock / Golems

Use this page to document the legit modules in `Skyblock / Golems`.

## Modules

### Location Scanner

**Current summary:** Placeholder module for golem location scanning.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Spawn Timer

**Current summary:** Placeholder module for golem spawn timing.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-general',
    'General',
    'Framework page for Dungeons / Floor 7 / General.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    10,
    '# Dungeons / Floor 7 / General

Use this page to document the legit modules in `Dungeons / Floor 7 / General`.

## Modules

### Wither ESP

**Current summary:** Renders configurable boxes around withers.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Positional Messages

**Current summary:** Sends saved commands once when you enter configured rings.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-predev',
    'Predev',
    'Framework page for Dungeons / Floor 7 / Predev.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    20,
    '# Dungeons / Floor 7 / Predev

Use this page to document the legit modules in `Dungeons / Floor 7 / Predev`.

## Modules

### Arrow Align

**Current summary:** Shows Arrow Align placements. The addon build can also expose auto-complete controls.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the solver settings here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Lights Device

**Current summary:** Shows the Lights Device solver. The addon build can also expose auto-complete controls.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the solver settings here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-phase-1',
    'Phase 1',
    'Framework page for Dungeons / Floor 7 / Phase 1.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    30,
    '# Dungeons / Floor 7 / Phase 1

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 1`.

## Modules

### Maxor HP% HUD

**Current summary:** Shows the Maxor boss bar percent as a movable HUD.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Archer Utils

**Current summary:** Spray and death bow helpers for Phase 1.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-phase-3',
    'Phase 3',
    'Framework page for Dungeons / Floor 7 / Phase 3.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    50,
    '# Dungeons / Floor 7 / Phase 3

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 3`.

## Modules

### Term GUI

**Current summary:** Replaces Floor 7 terminals with a compact custom solver GUI.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the legit GUI settings here._

**Notes / caveats:** _Document addon-only queue and hover settings in the addon wiki._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-phase-4',
    'Phase 4',
    'Framework page for Dungeons / Floor 7 / Phase 4.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    60,
    '# Dungeons / Floor 7 / Phase 4

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 4`.

## Modules

### 3x3 Highlight

**Current summary:** Placeholder module for highlighting the Phase 4 3x3 area.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-floor-7-phase-5',
    'Phase 5',
    'Framework page for Dungeons / Floor 7 / Phase 5.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    70,
    '# Dungeons / Floor 7 / Phase 5

Use this page to document the legit modules in `Dungeons / Floor 7 / Phase 5`.

## Modules

### Cow Hat

**Current summary:** Placeholder legit module for Cow Hat reminders.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _Document the Cow Hat Reminder toggle and reminder radius here._

**Notes / caveats:** _Document the addon-only auto-equip behavior in the addon wiki._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-general',
    'General',
    'Framework page for Kuudra / General.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    10,
    '# Kuudra / General

Use this page to document the legit modules in `Kuudra / General`.

## Modules

### Block Pickobulus

**Current summary:** Placeholder module for blocking Pickobulus interactions.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-1',
    'Phase 1',
    'Framework page for Kuudra / Phase 1.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    20,
    '# Kuudra / Phase 1

Use this page to document the legit modules in `Kuudra / Phase 1`.

## Modules

### Kuudra Waypoints

**Current summary:** Pearl helper and pile routing overlays for Phase 1.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Pearl Prediction

**Current summary:** Predicts pearl landing blocks and highlights pile proximity.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-2',
    'Phase 2',
    'Framework page for Kuudra / Phase 2.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    30,
    '# Kuudra / Phase 2

Use this page to document the legit modules in `Kuudra / Phase 2`.

## Modules

### Build Progress Display

**Current summary:** Placeholder module for build progress tracking.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-3',
    'Phase 3',
    'Framework page for Kuudra / Phase 3.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    40,
    '# Kuudra / Phase 3

Use this page to document the legit modules in `Kuudra / Phase 3`.

## Modules

### Stun Waypoint

**Current summary:** Placeholder module for stun waypoint rendering.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-misc-other',
    'Other',
    'Framework page for Misc / Other.',
    'wiki',
    'public',
    'Misc',
    NULL,
    20,
    '# Misc / Other

Use this page to document the legit modules in `Misc / Other`.

## Modules

### Discord Rich Presence

**Current summary:** Shares client status in Discord rich presence.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Heads Scale

**Current summary:** Resizes decorative heads and skull models.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Trail

**Current summary:** Renders a trail under your feet.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-wiki-guide',
    'How To Use This Wiki',
    'Guide for editing and extending the addon wiki structure.',
    'wiki',
    'addon',
    'Misc',
    NULL,
    5,
    '# How To Use This Wiki

This is the addon wiki. It only documents addon-only features and addon-only automation settings.

## Rules

- Use `/portal/wiki` for addon-only modules and addon-only automation.
- Keep legit-safe explanations in the public wiki.
- If a shared legit module gains addon automation, document only the extra addon settings here.
- Keep page titles and module names aligned with the in-game sidebar.

## Editing checklist

1. Document what the addon changes compared with the legit version.
2. Call out automation toggles, extra routes, or private setup steps.
3. Add caveats and account for license-gated behavior where needed.
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-dungeons-floor-7-predev',
    'Predev',
    'Framework page for Dungeons / Floor 7 / Predev.',
    'wiki',
    'addon',
    'Dungeons',
    'Floor 7',
    20,
    '# Dungeons / Floor 7 / Predev

Use this page to document the addon-only automation in `Dungeons / Floor 7 / Predev`.

## Modules

### Arrow Align

**Current summary:** The addon build exposes Arrow Align auto-complete on top of the legit solver.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _Document the solver toggle and auto-complete toggle here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Lights Device

**Current summary:** The addon build exposes Lights Device auto-complete on top of the legit solver.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _Document the solver toggle and auto-complete toggle here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-dungeons-floor-7-phase-3',
    'Phase 3',
    'Framework page for Dungeons / Floor 7 / Phase 3.',
    'wiki',
    'addon',
    'Dungeons',
    'Floor 7',
    50,
    '# Dungeons / Floor 7 / Phase 3

Use this page to document the addon-only automation in `Dungeons / Floor 7 / Phase 3`.

## Modules

### Auto SS

**Current summary:** Ports Simon Shut Up AutoSS for Phase 3 Simon Says.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Term GUI

**Current summary:** The addon build exposes Queue Terms and Hover Terms on top of the legit Term GUI.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _Document Queue Terms, Hover Terms, and timing settings here._

**Notes / caveats:** _Document the base GUI in the public wiki and keep addon-only automation here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-kuudra-phase-1',
    'Phase 1',
    'Framework page for Kuudra / Phase 1.',
    'wiki',
    'addon',
    'Kuudra',
    NULL,
    20,
    '# Kuudra / Phase 1

Use this page to document the addon-only modules in `Kuudra / Phase 1`.

## Modules

### Auto Pearls

**Current summary:** Queues automated pearl timing helpers for private Kuudra routes.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Auto Route To Pre Locations

**Current summary:** Follows saved preroutes to missing pre spots.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._

### Crate Aura

**Current summary:** Automates crate handling and related pre tasks.

**How to use it:** _Add activation and workflow details here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add caveats, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-misc-other',
    'Other',
    'Framework page for Misc / Other.',
    'wiki',
    'addon',
    'Misc',
    NULL,
    20,
    '# Misc / Other

Use this page to document the addon-only modules in `Misc / Other`.

## Modules

_No addon-only modules are currently mapped to this sidebar entry._

### Future module placeholder

**Overview:** _Add the module purpose here._

**How to use it:** _Add activation and setup steps here._

**Key settings:** _List the important toggles and sliders here._

**Notes / caveats:** _Add limitations, examples, and route notes here._
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

-- BEGIN SITE ACCESS REFRESH
INSERT INTO site_settings (key, value)
VALUES
    ('support_url', '/login?mode=addon'),
    ('download_url', '/wiki'),
    ('source_code_url', 'https://github.com/mrailouis/larpclient-public')
ON CONFLICT(key) DO UPDATE SET
    value = excluded.value,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM auth_logs WHERE license_key IS NOT NULL;
DELETE FROM client_sessions WHERE client_type = 'larp-addon' OR license_key IS NOT NULL;
DELETE FROM licenses;
DELETE FROM sqlite_sequence WHERE name = 'licenses';

DELETE FROM site_documents;
DELETE FROM sqlite_sequence WHERE name = 'site_documents';

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-home',
    'LarpClient',
    'Public legit docs, live status, and a separate addon lane with its own login model.',
    'page',
    'public',
    NULL,
    NULL,
    0,
    '# Public Side

LarpClient keeps the public side focused on legit modules, clear information, and route notes that match the in game sidebar.

## What lives here

- The legit wiki at `/wiki`
- Public UI cleanup and route helpers
- Shared live presence counts from the mod and addon heartbeat

## Current legit sections

- Skyblock: Server Lag Detection, Mute ID Hider, Location Scanner, Spawn Timer
- Dungeons: True Splits, Last Breath Utils, Wither ESP, Positional Messages, Arrow Align, Lights Device, Maxor HUD, Archer Utils, Gyro Waypoints, Term GUI, 3x3 Highlight, Cow Hat
- Kuudra: Block Pickobulus, Kuudra Waypoints, Pearl Prediction, Build Progress Display, Stun Waypoint
- Misc: Clean Scoreboard, Module List, Item Rarity, Discord Rich Presence, Heads Scale, Trail

## Access split

- The legit wiki is public.
- The addon area is separate and requires a valid addon license.
- Admin editing sits behind the root login for `mrai`.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-home',
    'Addon Area',
    'Private automation docs, private videos, and a stricter addon login flow tied to each license.',
    'page',
    'addon',
    NULL,
    NULL,
    0,
    '# Addon Area

This area only covers shipped addon only modules and addon only behavior.

## Sign in model

1. Enter the addon license key.
2. Enter the Discord name stored on that license.
3. On first site login, create a portal password.
4. After that, the same portal password unlocks the addon area.

## What is documented here

- Addon only automation modules
- Addon only command flows and route tools
- Addon only behavior layered on top of public modules

## What is not here

- Base legit setup for public modules
- Anything that already belongs in the legit wiki
- Unshipped ideas that are not registered in the client
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-wiki-guide',
    'Wiki Guide',
    'How the legit wiki is organized and how to keep it aligned with the client sidebar.',
    'wiki',
    'public',
    'Misc',
    NULL,
    5,
    '# Legit Wiki Guide

The public wiki mirrors the legit sidebar instead of grouping pages into marketing cards.

## Structure

- Top level sections follow the client groups: Skyblock, Dungeons, Kuudra, Misc.
- Floor 7 pages live under the Dungeons group as the `Floor 7` branch.
- Page titles stay the same as the sidebar entry names such as General, Predev, Phase 1, UI, or Other.

## Editing rule set

- Keep only legit behavior on the public side.
- If a public module gains addon only automation, document the base behavior here and move the automation details into the addon wiki.
- When a module is still a placeholder in code, say that clearly instead of pretending the full gameplay logic is already present.

## Good page format

- One short overview at the top
- One section per live module in that sidebar slot
- Short setup notes, route notes, and caveats for the current implementation
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-skyblock-general',
    'General',
    'Public Skyblock general modules.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    10,
    '# Skyblock / General

## Server Lag Detection

Displays the amount of time the server lagged for.

## Mute ID Hider

Removes spam from mute messages.

## Femboy Arrows

Replaces arrow hit sounds with meowing.

## Improved Skyblock Menus

Uses NEU style improved skyblock menus (cred. NotEnoughUpdates)

## Slot Locking

Uses NEU style slot locking and binding (cred. NotEnoughUpdates)

## Visual FME

Purely cosmetic retexturing of specific blocks to enhance gameplay.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-skyblock-golems',
    'Golems',
    'Public Skyblock golems modules.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    20,
    '# Skyblock / Golems

## Location Scanner

Finds the location of the End Stone Protector in the End.

## Spawn Timer

Shows a timer until the End Stone Protector will spawn, also tracks any spawn delays.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-dungeons-general',
    'General',
    'Public Dungeons general modules.',
    'wiki',
    'public',
    'Dungeons',
    NULL,
    10,
    '# Dungeons / General

## True Splits

Port of True Splits from 1.8.9. Tracks both clear/boss splits and an in-depth breakdown of M7 splits.

## Last Breath Utils

Tracks how many Last Breath shots landed on any mob.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-general',
    'General',
    'Public Floor 7 general modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    10,
    '# Dungeons / Floor 7 / General

## Wither ESP

Renders a ESP hitbox around withers.

## Positional Messages

Sends saved messages when walking into their rings.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-predev',
    'Predev',
    'Public Floor 7 predev modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    20,
    '# Dungeons / Floor 7 / Predev

## Arrow Align

Shows the required amount of rotations on each slot to finish the device.

## Lights (Lever) Device

Shows which levers to flip to complete the device.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-phase-1',
    'Phase 1',
    'Public Floor 7 phase 1 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    30,
    '# Dungeons / Floor 7 / Phase 1

## Maxor HP % HUD

Shows Maxor''s HP as a percentage moveable HUD (useful for M/B)

## Archer Utils

Tells you how many mobs sprayed and killed with Ice Spray and Death Bow.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-phase-2',
    'Phase 2',
    'Public Floor 7 phase 2 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    40,
    '# Dungeons / Floor 7 / Phase 2

## Gyro Waypoints

Renders waypoints to use the Gyrokinetic Wand in Phase 2.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-phase-3',
    'Phase 3',
    'Public Floor 7 phase 3 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    50,
    '# Dungeons / Floor 7 / Phase 3

## Terminal GUI

Fully customizable terminal gui. (Inspo. soshimee)
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-phase-4',
    'Phase 4',
    'Public Floor 7 phase 4 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    60,
    '# Dungeons / Floor 7 / Phase 4

## 3x3 Highlight

Highlights the 3x3 area to break to prevent platform despawning.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-floor7-phase-5',
    'Phase 5',
    'Public Floor 7 phase 5 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    70,
    '# Dungeons / Floor 7 / Phase 5

## Cow Hat Reminder

Reminds you to equip cow hat in Phase 5
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-general',
    'General',
    'Public Kuudra general modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    10,
    '# Kuudra / General

## Block Pickobulus

Prevents right-clicking any pickaxe/drill until build is complete.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-1',
    'Phase 1',
    'Public Kuudra phase 1 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    20,
    '# Kuudra / Phase 1

## Kuudra Waypoints

Dynamic waypoints for Sky/Flat/Pre pearls

## Pearl Prediction

Renders a waypoint where pearls will land, with an error ring showing the variance of where the pearl could land.

Informs the player if the supply will not place
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-2',
    'Phase 2',
    'Public Kuudra phase 2 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    30,
    '# Kuudra / Phase 2

## Build Progress Display

Moveable Hud element displaying the build progress
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-3',
    'Phase 3',
    'Public Kuudra phase 3 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    40,
    '# Kuudra / Phase 3

## Stun Waypoint

Fully configurable stun waypoint for insta-stun.

## Skip Waypoint

Fully configurable waypoint for etherwarping after stun to skip.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-kuudra-phase-4',
    'Phase 4',
    'Public Kuudra phase 4 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    50,
    '# Kuudra / Phase 4

## Kuudra Direction

Displays Kuudra''s peek direction
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-misc-ui',
    'UI',
    'Public UI modules.',
    'wiki',
    'public',
    'Misc',
    NULL,
    10,
    '# Misc / UI

## ClickGUI Colours

Full customization of the ClickGUI

## Clean Scoreboard

Smoother Scoreboard

## Module List

Lists all the modules currently active in a moveable HUD

## Item Rarity

Renders an item''s rarity as a background (Inspo. SBE)

## Player Customizations

If whitelisted change player size and name for all users of the mod, if not, simple clientside player resizing and naming.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'public-misc-other',
    'Other',
    'Public other modules.',
    'wiki',
    'public',
    'Misc',
    NULL,
    20,
    '# Misc / Other

## Discord RP

Shows larping on Discord.

## Head Scale

Rescales the size of head items 0.9 is the default on 1.8.9)

## Trail

Renders a trail where you move.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-wiki-guide',
    'Wiki Guide',
    'How the addon wiki is split away from the public side.',
    'wiki',
    'addon',
    'Misc',
    NULL,
    5,
    '# Addon Wiki Guide

The addon wiki only documents private modules and private behavior.

## What belongs here

- Addon only modules
- Addon only automation attached to public modules
- Private route editing and command driven tools

## What stays out

- Basic legit setup for public modules
- Public route information that already exists in the legit wiki
- Placeholder ideas that are not actually registered in the addon

## Editing rule set

- Keep the page tree aligned with the addon access area.
- Be explicit when a page covers private settings layered on top of a public module.
- Keep one page per sidebar slot instead of scattering one route across many tiny pages.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES
(
    'public-skyblock-general',
    'General',
    'Public Skyblock general modules.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    10,
    '# Skyblock / General

## Server Lag Detection

- Displays the amount of time the server lagged for.

## Mute ID Hider

- Removes spam from mute messages.

## Femboy Arrows

- Replaces arrow hit sounds with meowing.

## Improved Skyblock Menus

- Uses NEU style improved skyblock menus (cred. NotEnoughUpdates)

## Slot Locking

- Uses NEU style slot locking and binding (cred. NotEnoughUpdates)

## Visual FME

- Purely cosmetic retexturing of specific blocks to enhance gameplay.
'
),
(
    'public-skyblock-golems',
    'Golems',
    'Public Skyblock golems modules.',
    'wiki',
    'public',
    'Skyblock',
    NULL,
    20,
    '# Skyblock / Golems

## Location Scanner

- Finds the location of the End Stone Protector in the End.

## Spawn Timer

- Shows a timer until the End Stone Protector will spawn, also tracks any spawn delays.
'
),
(
    'public-dungeons-general',
    'General',
    'Public Dungeons general modules.',
    'wiki',
    'public',
    'Dungeons',
    NULL,
    10,
    '# Dungeons / General

## True Splits

- Port of True Splits from 1.8.9. Tracks both clear/boss splits and an in-depth breakdown of M7 splits.

## Last Breath Utils

- Tracks how many Last Breath shots landed on any mob.
'
),
(
    'public-floor7-general',
    'General',
    'Public Floor 7 general modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    10,
    '# Dungeons / Floor 7 / General

## Wither ESP

- Renders a ESP hitbox around withers.

## Positional Messages

- Sends saved messages when walking into their rings.
'
),
(
    'public-floor7-predev',
    'Predev',
    'Public Floor 7 predev modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    20,
    '# Dungeons / Floor 7 / Predev

## Arrow Align

- Shows the required amount of rotations on each slot to finish the device.

## Lights (Lever) Device

- Shows which levers to flip to complete the device.
'
),
(
    'public-floor7-phase-1',
    'Phase 1',
    'Public Floor 7 phase 1 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    30,
    '# Dungeons / Floor 7 / Phase 1

## Maxor HP % HUD

- Shows Maxor''s HP as a percentage moveable HUD (useful for M/B)

## Archer Utils

- Tells you how many mobs sprayed and killed with Ice Spray and Death Bow.
'
),
(
    'public-floor7-phase-2',
    'Phase 2',
    'Public Floor 7 phase 2 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    40,
    '# Dungeons / Floor 7 / Phase 2

## Gyro Waypoints

- Renders waypoints to use the Gyrokinetic Wand in Phase 2.
'
),
(
    'public-floor7-phase-3',
    'Phase 3',
    'Public Floor 7 phase 3 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    50,
    '# Dungeons / Floor 7 / Phase 3

## Terminal GUI

- Fully customizable terminal gui. (Inspo. soshimee)
'
),
(
    'public-floor7-phase-4',
    'Phase 4',
    'Public Floor 7 phase 4 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    60,
    '# Dungeons / Floor 7 / Phase 4

## 3x3 Highlight

- Highlights the 3x3 area to break to prevent platform despawning.
'
),
(
    'public-floor7-phase-5',
    'Phase 5',
    'Public Floor 7 phase 5 modules.',
    'wiki',
    'public',
    'Dungeons',
    'Floor 7',
    70,
    '# Dungeons / Floor 7 / Phase 5

## Cow Hat Reminder

- Reminds you to equip cow hat in Phase 5
'
),
(
    'public-kuudra-general',
    'General',
    'Public Kuudra general modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    10,
    '# Kuudra / General

## Block Pickobulus

- Prevents right-clicking any pickaxe/drill until build is complete.
'
),
(
    'public-kuudra-phase-1',
    'Phase 1',
    'Public Kuudra phase 1 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    20,
    '# Kuudra / Phase 1

## Kuudra Waypoints

- Dynamic waypoints for Sky/Flat/Pre pearls

## Pearl Prediction

- Renders a waypoint where pearls will land, with an error ring showing the variance of where the pearl could land.
- Informs the player if the supply will not place
'
),
(
    'public-kuudra-phase-2',
    'Phase 2',
    'Public Kuudra phase 2 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    30,
    '# Kuudra / Phase 2

## Build Progress Display

- Moveable Hud element displaying the build progress
'
),
(
    'public-kuudra-phase-3',
    'Phase 3',
    'Public Kuudra phase 3 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    40,
    '# Kuudra / Phase 3

## Stun Waypoint

- Fully configurable stun waypoint for insta-stun.

## Skip Waypoint

- Fully configurable waypoint for etherwarping after stun to skip.
'
),
(
    'public-kuudra-phase-4',
    'Phase 4',
    'Public Kuudra phase 4 modules.',
    'wiki',
    'public',
    'Kuudra',
    NULL,
    50,
    '# Kuudra / Phase 4

## Kuudra Direction

- Displays Kuudra''s peek direction
'
),
(
    'public-misc-ui',
    'UI',
    'Public UI modules.',
    'wiki',
    'public',
    'Misc',
    NULL,
    10,
    '# Misc / UI

## ClickGUI Colours

- Full customization of the ClickGUI

## Clean Scoreboard

- Smoother Scoreboard

## Module List

- Lists all the modules currently active in a moveable HUD

## Item Rarity

- Renders an item''s rarity as a background (Inspo. SBE)

## Player Customizations

- If whitelisted change player size and name for all users of the mod, if not, simple clientside player resizing and naming.
'
),
(
    'public-misc-other',
    'Other',
    'Public other modules.',
    'wiki',
    'public',
    'Misc',
    NULL,
    20,
    '# Misc / Other

## Discord RP

- Shows larping on Discord.

## Head Scale

- Rescales the size of head items 0.9 is the default on 1.8.9)

## Trail

- Renders a trail where you move.
'
)
ON CONFLICT(slug) DO UPDATE SET
    title = excluded.title,
    excerpt = excluded.excerpt,
    kind = excluded.kind,
    audience = excluded.audience,
    category = excluded.category,
    subcategory = excluded.subcategory,
    sort_order = excluded.sort_order,
    body_markdown = excluded.body_markdown,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-skyblock-general',
    'General',
    'Addon Skyblock general modules.',
    'wiki',
    'addon',
    'Skyblock',
    NULL,
    10,
    '# Skyblock / General

The addon keeps the private Skyblock tools grouped in one place because they are route and action heavy.

## Blink

Blink is the largest private Skyblock tool in this section. It handles packet based route logic and is meant for people who already know the route they are building around.

## Cancel Interact

Cancel Interact blocks unwanted interactions during private route setups where accidental clicks can ruin the flow.

## CGY Wardrobe

CGY Wardrobe is a private convenience layer for wardrobe handling inside the addon workspace.

## Simple Autoroutes

Simple Autoroutes stores lighter route definitions and is useful when you need repeated pathing without pulling in a larger route editor.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-dungeons-general',
    'General',
    'Addon Dungeons general modules.',
    'wiki',
    'addon',
    'Dungeons',
    NULL,
    10,
    '# Dungeons / General

## Dungeonbreaker Nuker

Dungeonbreaker Nuker is private because it is no longer just information. It sits firmly on the addon side and should be treated as part of the private automation stack.

## Velocity Buffer

Velocity Buffer is the safer utility layer in this section, but it still belongs in the addon because it adds private control over movement timing and recovery.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-floor7-phase-3',
    'Phase 3',
    'Addon Floor 7 phase 3 modules and private term behavior.',
    'wiki',
    'addon',
    'Dungeons',
    'Floor 7',
    50,
    '# Dungeons / Floor 7 / Phase 3

## Auto P3

Auto P3 is the core private Phase 3 module. It is the reason the addon registers the `.p3` command root and it owns the heavy ring and route automation side of the phase.

## Auto SS

Auto SS handles the private SS behavior for Phase 3. It belongs here because it is a direct automation layer rather than a public readability tool.

## Term GUI addon settings

The base Term GUI is public, but the hover and queue behavior stay documented here because those settings are only meaningful when addon automation is available.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-floor7-phase-5',
    'Phase 5',
    'Addon Floor 7 phase 5 modules.',
    'wiki',
    'addon',
    'Dungeons',
    'Floor 7',
    70,
    '# Dungeons / Floor 7 / Phase 5

## Auto Cow Hat

Auto Cow Hat is the private automation side of the Cow Hat feature set. The reminder slot lives on the public side, while the auto behavior stays here.

## Arrow Stack Waypoints

Arrow Stack Waypoints is a private route aid for the end of the fight and is grouped here with the other Phase 5 tools.

## Debuff Snap

Debuff Snap adds a private snap and ring oriented toolset for the phase and is exposed through the addon command layer as well.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-kuudra-general',
    'General',
    'Addon Kuudra general modules.',
    'wiki',
    'addon',
    'Kuudra',
    NULL,
    10,
    '# Kuudra / General

## Left Click Shop

Left Click Shop is a small private quality of life module that changes how the Kuudra shop flow feels. It is not public because it sits in the addon convenience layer and is only relevant once addon access is already present.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-kuudra-phase-1',
    'Phase 1',
    'Addon Kuudra phase 1 modules.',
    'wiki',
    'addon',
    'Kuudra',
    NULL,
    20,
    '# Kuudra / Phase 1

## Auto Pearls

Auto Pearls is the private execution layer for pearl throws. The public wiki covers reading throws with Pearl Prediction, while the addon handles actually scheduling them.

## Auto Route to Pre Locations

Auto Route to Pre Locations covers private preroute movement into pre spots. It is paired with the preroute editing commands documented in the addon area.

## Crate Aura

Crate Aura is another direct private action module. It belongs here because it affects how the phase is actually played, not just how it is visualized.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-kuudra-phase-3',
    'Phase 3',
    'Addon Kuudra phase 3 modules.',
    'wiki',
    'addon',
    'Kuudra',
    NULL,
    40,
    '# Kuudra / Phase 3

## Skip Waypoint

Skip Waypoint stays on the addon side. The public wiki documents the legit Stun Waypoint slot, while this page covers the private skip oriented routing that only exists in the addon.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-kuudra-phase-4',
    'Phase 4',
    'Addon Kuudra phase 4 modules.',
    'wiki',
    'addon',
    'Kuudra',
    NULL,
    50,
    '# Kuudra / Phase 4

## Backbone Lock

Backbone Lock is a pure private addon module for the late fight flow. It belongs here because it is route specific, action heavy, and not part of the public reading layer.
'
);

INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
VALUES (
    'addon-misc-other',
    'Other',
    'Addon other tools and command driven helpers.',
    'wiki',
    'addon',
    'Misc',
    NULL,
    20,
    '# Misc / Other

## Etherwarp Helper

Etherwarp Helper is command driven rather than a normal sidebar module, but it is still a real addon feature. The addon wires the helper controller into the private tick and render flow and exposes it through the addon command layer.

## Private command utilities

The addon also extends `.larp` with private utilities such as velocity buffer controls, debuff ring placement, preroute editing, blink route editing, and Cow Hat zone commands. They live here because they are not all backed by separate sidebar modules.
'
);
-- END SITE ACCESS REFRESH
