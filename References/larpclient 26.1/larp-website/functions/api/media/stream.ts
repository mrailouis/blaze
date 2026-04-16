import { canAccessAudience, getSiteSession } from "../../_lib/site";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
    MEDIA_BUCKET: R2Bucket;
}

type VideoRow = {
    id: number;
    slug: string;
    audience: "public" | "addon";
    r2_key: string;
};

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const url = new URL(request.url);
    const id = Number.parseInt(url.searchParams.get("id") ?? "", 10);

    if (!Number.isFinite(id)) {
        return Response.json({ ok: false, error: "Missing id" }, { status: 400 });
    }

    const video = await env.DB.prepare(`
        SELECT id, slug, audience, r2_key
        FROM site_videos
        WHERE id = ?
    `).bind(id).first<VideoRow>();

    if (!video) {
        return Response.json({ ok: false, error: "Not found" }, { status: 404 });
    }

    const session = await getSiteSession(request, env);
    if (!canAccessAudience(session, video.audience)) {
        return Response.json({ ok: false, error: "Forbidden" }, { status: 403 });
    }

    const object = await env.MEDIA_BUCKET.get(video.r2_key, {
        range: request.headers
    });

    if (!object) {
        return Response.json({ ok: false, error: "Missing object" }, { status: 404 });
    }

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("etag", object.httpEtag);
    headers.set("Accept-Ranges", "bytes");
    headers.set("Content-Disposition", `inline; filename="${video.slug}"`);
    headers.set("Cache-Control", video.audience === "public" ? "public, max-age=300" : "private, max-age=60");

    const rangeHeader = request.headers.get("range");
    if (rangeHeader && object.range && "offset" in object.range && "length" in object.range) {
        const start = object.range.offset;
        const end = object.range.offset + object.range.length - 1;
        headers.set("Content-Range", `bytes ${start}-${end}/${object.size}`);
        headers.set("Content-Length", object.range.length.toString());
        return new Response(object.body, { status: 206, headers });
    }

    headers.set("Content-Length", object.size.toString());
    return new Response(object.body, { status: 200, headers });
};
