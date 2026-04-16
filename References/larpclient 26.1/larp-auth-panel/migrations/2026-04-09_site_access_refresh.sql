ALTER TABLE licenses ADD COLUMN discord_name TEXT NOT NULL DEFAULT '';
ALTER TABLE licenses ADD COLUMN portal_password_hash TEXT;
ALTER TABLE licenses ADD COLUMN portal_password_salt TEXT;
ALTER TABLE licenses ADD COLUMN portal_password_set_at TEXT;

CREATE INDEX IF NOT EXISTS idx_licenses_discord_name
    ON licenses (discord_name);

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
