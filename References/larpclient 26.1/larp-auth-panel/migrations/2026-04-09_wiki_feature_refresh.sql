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
