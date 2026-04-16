import {
    createPasswordSalt,
    findPortalLicense,
    hashPortalPassword,
    MIN_PORTAL_PASSWORD_LENGTH,
    validatePortalLicense,
    verifyPortalPassword
} from "../../_lib/licenses";
import { buildSessionCookie, createSession } from "../../_lib/session";

interface Env {
    DB: D1Database;
    SITE_SESSION_SECRET: string;
}

export const onRequestPost = async ({ request, env }: { request: Request; env: Env }) => {
    try {
        const body = await request.json() as { licenseKey?: string; discordName?: string; password?: string };
        const licenseKey = body.licenseKey?.trim();
        const discordName = body.discordName?.trim();
        const password = body.password?.trim() ?? "";

        if (!licenseKey || !discordName || !password) {
            return Response.json({ ok: false, error: "License, Discord name, and password are required" }, { status: 400 });
        }

        if (password.length < MIN_PORTAL_PASSWORD_LENGTH) {
            return Response.json({
                ok: false,
                error: `Password must be at least ${MIN_PORTAL_PASSWORD_LENGTH} characters`
            }, { status: 400 });
        }

        const result = await validatePortalLicense(env.DB, licenseKey, discordName);
        if (!result.ok) {
            return Response.json({ ok: false, error: result.error }, { status: result.status });
        }

        const license = result.license;
        const hasPassword = Boolean(license.portal_password_hash && license.portal_password_salt);

        if (!hasPassword) {
            const salt = createPasswordSalt();
            const hash = await hashPortalPassword(password, salt);

            await env.DB.prepare(`
                UPDATE licenses
                SET portal_password_salt = ?, portal_password_hash = ?, portal_password_set_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE license_key = ?
            `).bind(salt, hash, license.license_key).run();
        } else {
            const validPassword = await verifyPortalPassword(
                password,
                license.portal_password_salt as string,
                license.portal_password_hash as string
            );

            if (!validPassword) {
                return Response.json({ ok: false, error: "Incorrect portal password" }, { status: 401 });
            }
        }

        const latestLicense = await findPortalLicense(env.DB, license.license_key);

        const token = await createSession(env.SITE_SESSION_SECRET, {
            role: "license",
            licenseKey: license.license_key,
            productTier: license.product_tier,
            discordName,
            portalPasswordSetAt: latestLicense?.portal_password_set_at ?? null
        });

        const headers = new Headers();
        headers.append("Set-Cookie", buildSessionCookie(request, token));

        return new Response(JSON.stringify({
            ok: true,
            passwordCreated: !hasPassword
        }), { status: 200, headers });
    } catch (error) {
        console.error("license-login failed", error);
        return Response.json({ ok: false, error: "Bad request" }, { status: 400 });
    }
};
