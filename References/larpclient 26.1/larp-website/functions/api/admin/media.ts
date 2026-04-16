import { fetchAllVideos, normalizeAudience, requireAdmin, slugify } from "../../_lib/site";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
    MEDIA_BUCKET: R2Bucket;
}

type ExistingVideo = {
    id: number;
    r2_key: string;
};

export const onRequest = async ({ request, env }: { request: Request; env: Env }) => {
    const admin = await requireAdmin(request, env);
    if (!admin) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    if (request.method === "GET") {
        const videos = await fetchAllVideos(env);
        return Response.json({
            ok: true,
            videos: videos.map((video) => ({
                ...video,
                streamUrl: `/api/media/stream?id=${video.id}`
            }))
        });
    }

    if (request.method === "POST") {
        try {
            const formData = await request.formData();
            const file = formData.get("file");
            const title = `${formData.get("title") ?? ""}`.trim();
            const description = `${formData.get("description") ?? ""}`.trim() || null;
            const audience = normalizeAudience(formData.get("audience"));
            const sortOrderRaw = `${formData.get("sortOrder") ?? "0"}`.trim();
            const sortOrder = Number.parseInt(sortOrderRaw, 10) || 0;

            if (!(file instanceof File) || !title) {
                return Response.json({ ok: false, error: "Title and file are required" }, { status: 400 });
            }

            const safeSlug = slugify(title) || `video-${Date.now()}`;
            const objectKey = `${audience}/${Date.now()}-${safeSlug}`;

            await env.MEDIA_BUCKET.put(objectKey, file.stream(), {
                httpMetadata: {
                    contentType: file.type || "application/octet-stream",
                    contentDisposition: "inline"
                }
            });

            await env.DB.prepare(`
                INSERT INTO site_videos (slug, title, description, audience, sort_order, r2_key, mime_type, size_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            `).bind(
                `${safeSlug}-${Date.now()}`,
                title,
                description,
                audience,
                sortOrder,
                objectKey,
                file.type || "application/octet-stream",
                file.size
            ).run();

            return Response.json({ ok: true });
        } catch {
            return Response.json({ ok: false, error: "Upload failed" }, { status: 500 });
        }
    }

    if (request.method === "DELETE") {
        try {
            const body = await request.json() as { id?: number };
            if (!body.id) {
                return Response.json({ ok: false, error: "Missing id" }, { status: 400 });
            }

            const video = await env.DB.prepare(`
                SELECT id, r2_key
                FROM site_videos
                WHERE id = ?
            `).bind(body.id).first<ExistingVideo>();

            if (!video) {
                return Response.json({ ok: false, error: "Not found" }, { status: 404 });
            }

            await env.MEDIA_BUCKET.delete(video.r2_key);
            await env.DB.prepare(`
                DELETE FROM site_videos
                WHERE id = ?
            `).bind(body.id).run();

            return Response.json({ ok: true });
        } catch {
            return Response.json({ ok: false, error: "Delete failed" }, { status: 500 });
        }
    }

    return new Response("Method not allowed", { status: 405 });
};
