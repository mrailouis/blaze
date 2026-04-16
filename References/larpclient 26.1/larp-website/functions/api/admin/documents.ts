import { fetchAllDocuments, normalizeAudience, normalizeKind, normalizeOptionalText, requireAdmin, slugify } from "../../_lib/site";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
}

export const onRequest = async ({ request, env }: { request: Request; env: Env }) => {
    const admin = await requireAdmin(request, env);
    if (!admin) {
        return Response.json({ ok: false, error: "Unauthorized" }, { status: 401 });
    }

    if (request.method === "GET") {
        const documents = await fetchAllDocuments(env);
        return Response.json({ ok: true, documents });
    }

    if (request.method === "POST") {
        try {
            const body = await request.json() as {
                id?: number;
                slug?: string;
                title?: string;
                excerpt?: string | null;
                kind?: string;
                audience?: string;
                category?: string | null;
                subcategory?: string | null;
                sortOrder?: number | string;
                bodyMarkdown?: string;
            };

            const title = body.title?.trim();
            const bodyMarkdown = body.bodyMarkdown?.trim() ?? "";
            const slug = slugify(body.slug?.trim() || title || "");

            if (!title || !slug || !bodyMarkdown) {
                return Response.json({ ok: false, error: "Title, slug, and markdown are required" }, { status: 400 });
            }

            const kind = normalizeKind(body.kind);
            const audience = normalizeAudience(body.audience);
            const excerpt = body.excerpt?.trim() || null;
            const category = kind === "wiki" ? normalizeOptionalText(body.category) : null;
            const subcategory = kind === "wiki" ? normalizeOptionalText(body.subcategory) : null;
            const parsedSortOrder = typeof body.sortOrder === "string"
                ? Number.parseInt(body.sortOrder, 10)
                : body.sortOrder;
            const sortOrder = Number.isFinite(parsedSortOrder) ? parsedSortOrder : 0;

            if (body.id) {
                await env.DB.prepare(`
                    UPDATE site_documents
                    SET slug = ?, title = ?, excerpt = ?, kind = ?, audience = ?, category = ?, subcategory = ?, sort_order = ?, body_markdown = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                `).bind(slug, title, excerpt, kind, audience, category, subcategory, sortOrder, bodyMarkdown, body.id).run();
            } else {
                await env.DB.prepare(`
                    INSERT INTO site_documents (slug, title, excerpt, kind, audience, category, subcategory, sort_order, body_markdown)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                `).bind(slug, title, excerpt, kind, audience, category, subcategory, sortOrder, bodyMarkdown).run();
            }

            return Response.json({ ok: true });
        } catch {
            return Response.json({ ok: false, error: "Save failed" }, { status: 500 });
        }
    }

    if (request.method === "DELETE") {
        try {
            const body = await request.json() as { id?: number };
            if (!body.id) {
                return Response.json({ ok: false, error: "Missing id" }, { status: 400 });
            }

            await env.DB.prepare(`
                DELETE FROM site_documents
                WHERE id = ?
            `).bind(body.id).run();

            return Response.json({ ok: true });
        } catch {
            return Response.json({ ok: false, error: "Delete failed" }, { status: 500 });
        }
    }

    return new Response("Method not allowed", { status: 405 });
};
