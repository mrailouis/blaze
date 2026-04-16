import { useEffect, useState, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { getSession } from "../lib/auth";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
    const [loading, setLoading] = useState(true);
    const [authed, setAuthed] = useState(false);

    useEffect(() => {
        let mounted = true;

        getSession()
            .then((value) => {
                if (mounted) {
                    setAuthed(value);
                    setLoading(false);
                }
            })
            .catch(() => {
                if (mounted) {
                    setAuthed(false);
                    setLoading(false);
                }
            });

        return () => {
            mounted = false;
        };
    }, []);

    if (loading) {
        return (
            <div className="page">
                <div className="glass card">Checking session...</div>
            </div>
        );
    }

    if (!authed) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
}