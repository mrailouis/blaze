import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { apiGet } from "../lib/api";

type OnlineUser = {
    uuid: string;
    minecraft_name: string | null;
    last_seen_at: string;
    client_type: string;
    license_key: string | null;
    product_tier: string | null;
};

type DashboardData = {
    ok: true;
    onlineUsers: OnlineUser[];
    onlineTotals: {
        total: number;
        mod: number;
        addon: number;
    };
    activeLicenses: number;
    revokedLicenses: number;
};

export default function DashboardPage() {
    const [data, setData] = useState<DashboardData | null>(null);
    const [error, setError] = useState("");

    async function load() {
        try {
            setError("");
            const result = await apiGet<DashboardData>("/api/admin/dashboard/stats");
            setData(result);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load dashboard");
        }
    }

    useEffect(() => {
        let cancelled = false;

        queueMicrotask(() => {
            if (!cancelled) {
                load();
            }
        });

        const interval = window.setInterval(load, 10_000);
        return () => {
            cancelled = true;
            window.clearInterval(interval);
        };
    }, []);

    if (error) {
        return <div className="glass card">{error}</div>;
    }

    if (!data) {
        return <div className="glass card">Loading...</div>;
    }

    return (
        <div className="grid">
            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Online Users</div>
                <div style={{ fontSize: 34, fontWeight: 800 }}>{data.onlineTotals.total}</div>
            </motion.div>

            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Larp Mod</div>
                <div style={{ fontSize: 34, fontWeight: 800 }}>{data.onlineTotals.mod}</div>
            </motion.div>

            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Larp Addon</div>
                <div style={{ fontSize: 34, fontWeight: 800 }}>{data.onlineTotals.addon}</div>
            </motion.div>

            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Active Licenses</div>
                <div style={{ fontSize: 34, fontWeight: 800 }}>{data.activeLicenses}</div>
            </motion.div>

            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Revoked Licenses</div>
                <div style={{ fontSize: 34, fontWeight: 800 }}>{data.revokedLicenses}</div>
            </motion.div>

            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Status</div>
                <span className="badge">Heartbeat Live</span>
            </motion.div>

            <motion.div className="glass card col-12" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Online Users</div>
                {data.onlineUsers.length === 0 ? (
                    <div className="muted">No online users.</div>
                ) : (
                    <div className="user-list">
                        {data.onlineUsers.map((user, i) => (
                            <div key={`${user.uuid}-${user.client_type}-${i}`} className="user-pill">
                                <div>
                                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                                        <div style={{ fontWeight: 700 }}>{user.minecraft_name || "Unknown User"}</div>
                                        <span className={`badge ${user.client_type === "larp-addon" ? "" : "neutral"}`}>
                                            {user.client_type === "larp-addon" ? "larp-addon" : "larp-mod"}
                                        </span>
                                        {user.product_tier && (
                                            <span className="badge neutral">{user.product_tier}</span>
                                        )}
                                    </div>
                                    <div className="muted" style={{ fontSize: 12 }}>{user.uuid}</div>
                                </div>
                                <div className="muted" style={{ fontSize: 12 }}>
                                    {new Date(user.last_seen_at).toLocaleTimeString()}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </motion.div>
        </div>
    );
}
