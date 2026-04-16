import { fetchDocuments, fetchOnlineTotals, fetchSettings, fetchVideos, getSiteSession } from "../../_lib/site";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
}

export const onRequestGet = async ({ request, env }: { request: Request; env: Env }) => {
    const session = await getSiteSession(request, env);

    const [settings, documents, videos, onlineTotals] = await Promise.all([
        fetchSettings(env),
        fetchDocuments(env, session),
        fetchVideos(env, session),
        fetchOnlineTotals(env)
    ]);

    const publicHome = documents.find((doc) => doc.kind === "page" && doc.slug === "public-home") ?? null;
    const addonHome = documents.find((doc) => doc.kind === "page" && doc.slug === "addon-home") ?? null;
    const publicWiki = documents.filter((doc) => doc.kind === "wiki" && doc.audience === "public");
    const addonWiki = documents.filter((doc) => doc.kind === "wiki" && doc.audience === "addon");
    const publicVideos = videos.filter((video) => video.audience === "public");
    const addonVideos = videos.filter((video) => video.audience === "addon");

    return Response.json({
        ok: true,
        session: session
            ? {
                role: session.role,
                productTier: session.productTier ?? null,
                discordName: session.discordName ?? null,
                username: session.username ?? null
            }
            : null,
        settings,
        onlineTotals,
        publicHome,
        addonHome,
        publicWiki,
        addonWiki,
        publicVideos,
        addonVideos
    });
};
