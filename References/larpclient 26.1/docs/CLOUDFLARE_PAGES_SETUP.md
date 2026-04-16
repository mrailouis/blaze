# Cloudflare Pages Setup

This repo now uses two Cloudflare Pages projects:

- `larp-auth-panel`
- `larp-website`

You do not need a separate standalone Worker. Both projects use Pages Functions.

## 1. Apply the D1 migration

From `larp-auth-panel`, run:

```bash
cd "larp-auth-panel"
npx wrangler d1 execute larp-auth-db --remote --file migrations/2026-04-08_site_presence.sql
```

That adds:

- `licenses.product_tier`
- `licenses.last_seen_at`
- `licenses.last_seen_client_type`
- `client_sessions`
- `site_settings`
- `site_documents`
- `site_videos`

If you already applied that migration on an existing database, also run:

```bash
cd "larp-auth-panel"
npx wrangler d1 execute larp-auth-db --remote --file migrations/2026-04-08_site_document_taxonomy.sql
```

That adds:

- `site_documents.category`
- `site_documents.subcategory`
- `site_settings.source_code_url`

Then seed the wiki framework:

```bash
cd "larp-auth-panel"
npx wrangler d1 execute larp-auth-db --remote --file migrations/2026-04-09_wiki_framework.sql
```

That:

- removes the old starter wiki placeholders
- creates public legit wiki pages that mirror the in-game sidebar
- creates addon-only wiki pages for the private feature set
- adds a guide page to each wiki with editing instructions

## 2. Configure `larp-auth-panel`

Bindings:

- D1: `DB`

Secrets / vars:

- `ADMIN_PASSWORD`
- `ADMIN_SESSION_SECRET`

Deploy:

```bash
cd "larp-auth-panel"
npm install
npm run build
npx wrangler pages deploy dist
```

## 3. Configure `larpclient.pages.dev`

Folder:

- `larp-website`

Bindings:

- D1: `DB`
- R2: `MEDIA_BUCKET`

Secrets / vars:

- `ADMIN_PASSWORD`
- `SITE_SESSION_SECRET`

Deploy:

```bash
cd "larp-website"
npm install
npm run build
npx wrangler pages deploy dist
```

## 4. R2 bucket

Create a bucket such as `larpclient-site-media`, then bind it to `MEDIA_BUCKET` in the `larp-website` Pages project.

The website streams videos through `/api/media/stream?id=...` and keeps the bucket private. This reduces casual downloading, but it cannot fully stop someone from saving a played video.

## 5. What is live after deploy

- Public homepage with legit feature content
- Public wiki
- License login for addon-only pages and wiki content
- Admin login with live markdown editor
- Admin video upload and streaming
- Online counts split between `larp-mod` and `larp-addon`
