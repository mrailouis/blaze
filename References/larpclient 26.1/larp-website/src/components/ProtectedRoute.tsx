import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { getSession } from "../lib/api";
import type { SessionInfo } from "../types";

type AllowedRole = "admin" | "license";

export default function ProtectedRoute(
    {
        allow,
        requireAddon,
        children
    }: {
        allow: AllowedRole[];
        requireAddon?: boolean;
        children: ReactNode;
    }
) {
    const [session, setSession] = useState<SessionInfo | undefined>(undefined);

    useEffect(() => {
        getSession()
            .then(setSession)
            .catch(() => setSession(null));
    }, []);

    if (session === undefined) {
        return (
            <div className="standalone-wrap">
                <div className="surface empty-panel">Checking session...</div>
            </div>
        );
    }

    if (!session || !allow.includes(session.role)) {
        const mode = requireAddon ? "addon" : (allow.length === 1 && allow[0] === "admin" ? "admin" : "addon");
        return <Navigate to={`/login?mode=${mode}`} replace />;
    }

    if (requireAddon && session.role === "license" && session.productTier !== "addon") {
        return <Navigate to="/login?mode=addon" replace />;
    }

    return <>{children}</>;
}
