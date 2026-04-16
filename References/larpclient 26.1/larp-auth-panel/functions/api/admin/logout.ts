import { buildLogoutCookie } from "../../_lib/adminAuth";

export const onRequestPost = async ({ request }: { request: Request }) => {
    const headers = new Headers();
    headers.append("Set-Cookie", buildLogoutCookie(request));

    return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers
    });
};