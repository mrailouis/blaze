import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiPost } from "../lib/api";
import { getSession } from "../lib/auth";

export default function LoginPage() {
    const [username, setUsername] = useState("mrai");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        getSession().then((authed) => {
            if (authed) {
                navigate("/", { replace: true });
            }
        });
    }, [navigate]);

    async function handleLogin() {
        try {
            setLoading(true);
            setError("");
            await apiPost("/api/admin/login", { username, password });
            navigate("/", { replace: true });
        } catch (e) {
            setError(e instanceof Error ? e.message : "Login failed");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="login-wrap">
            <motion.div
                className="glass login-card"
                initial={{ opacity: 0, scale: 0.96, y: 10 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
            >
                <div className="brand" style={{ marginBottom: 18 }}>
                    <span className="aqua">Larp</span>
                    <span className="pink">Auth</span>
                </div>

                <h1 className="card-title">Admin Login</h1>
                <p className="muted">Root user is <code>mrai</code>. Sign in to manage licenses, Discord names, and portal access.</p>

                <div style={{ marginTop: 14 }}>
                    <label className="label">Username</label>
                    <input
                        className="input"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === "Enter") handleLogin();
                        }}
                    />
                </div>

                <div style={{ marginTop: 14 }}>
                    <label className="label">Password</label>
                    <input
                        className="input"
                        type="password"
                        placeholder="••••••••"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === "Enter") handleLogin();
                        }}
                    />
                </div>

                {error && (
                    <div style={{ marginTop: 12, color: "#ff8bcf" }}>{error}</div>
                )}

                <div style={{ marginTop: 18 }}>
                    <button className="button" style={{ width: "100%" }} onClick={handleLogin} disabled={loading}>
                        {loading ? "Signing in..." : "Sign in"}
                    </button>
                </div>
            </motion.div>
        </div>
    );
}
