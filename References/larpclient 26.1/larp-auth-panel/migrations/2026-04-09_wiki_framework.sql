DELETE FROM site_documents
WHERE slug IN ('getting-started', 'addon-quickstart');

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
