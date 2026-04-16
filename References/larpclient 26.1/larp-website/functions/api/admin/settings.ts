import { fetchSettings, requireAdmin } from "../../_lib/site";

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
        return Response.json({
            ok: true,
            settings: await fetchSettings(env)
        });
    }

    if (request.method === "POST") {
        try {
            const body = await request.json() as { settings?: Record<string, string> };
            const entries = Object.entries(body.settings ?? {});

            for (const [key, value] of entries) {
                await env.DB.prepare(`
                    INSERT INTO site_settings (key, value)
                    VALUES (?, ?)
                    ON CONFLICT(key) DO UPDATE SET
                        value = excluded.value,
                        updated_at = CURRENT_TIMESTAMP
                `).bind(key, value).run();
            }

            return Response.json({ ok: true });
        } catch {
            return Response.json({ ok: false, error: "Save failed" }, { status: 500 });
        }
    }

    return new Response("Method not allowed", { status: 405 });
};
