# LarpClient Website

Cloudflare Pages app for `larpclient.pages.dev`.

## Features

- Public landing page for legit features
- Public wiki
- License login for addon-only pages and wiki entries
- Admin login with live markdown editor and video upload
- Online user cards fed by the shared `client_sessions` table

## Cloudflare setup

1. Create a Pages project and point it at the `larp-website` folder.
2. Bind the shared D1 database to `DB`.
3. Create an R2 bucket for videos and bind it to `MEDIA_BUCKET`.
4. Add these variables/secrets in Pages:
   - `ADMIN_PASSWORD`
   - `SITE_SESSION_SECRET`
5. Redeploy after adding bindings.

Cloudflare Pages Functions can access D1 and R2 directly, so you do not need a separate Worker for this setup.

## Local development

```bash
cd "larp-website"
npm install
npx wrangler pages dev dist --d1 DB=42493bed-a9ac-4fa4-8500-8ae21485121d --r2=MEDIA_BUCKET
```

In a second terminal:

```bash
cd "larp-website"
npm run dev
```

## Notes

- Video downloads cannot be fully prevented once a browser can play the file. This project streams videos inline, hides the normal download control, and keeps the bucket non-public, but a determined user can still save the stream.
- Run the SQL in `../larp-auth-panel/schema.sql` against your D1 database before deploying.
