import { hasPortalPassword, validatePortalLicense } from "../../_lib/licenses";

interface Env {
    DB: D1Database;
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    try {
        const body = await request.json() as { licenseKey?: string; discordName?: string };
        const licenseKey = body.licenseKey?.trim();
        const discordName = body.discordName?.trim();

        if (!licenseKey || !discordName) {
            return Response.json({ ok: false, error: "License and Discord name are required" }, { status: 400 });
        }

        const result = await validatePortalLicense(env.DB, licenseKey, discordName);
        if (!result.ok) {
            return Response.json({ ok: false, error: result.error }, { status: result.status });
        }

        return Response.json({
            ok: true,
            passwordSet: hasPortalPassword(result.license),
            licenseKey: result.license.license_key,
            discordName: result.license.discord_name
        });
    } catch {
        return Response.json({ ok: false, error: "Bad request" }, { status: 400 });
    }
};
