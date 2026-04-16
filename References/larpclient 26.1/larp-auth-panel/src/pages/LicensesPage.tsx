import { motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost } from "../lib/api";

type License = {
    id: number;
    license_key: string;
    bound_uuid: string | null;
    discord_name: string | null;
    portal_password_set_at: string | null;
    last_seen_name: string | null;
    last_seen_client_type: string | null;
    status: string;
    expires_at: string;
    product_tier: string;
};

export default function LicensesPage() {
    const [rows, setRows] = useState<License[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [search, setSearch] = useState("");

    async function load() {
        try {
            setLoading(true);
            setError("");
            const data = await apiGet<{ ok: true; licenses: License[] }>("/api/admin/licenses/list");
            setRows(data.licenses);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        load();
    }, []);

    async function updateLicense(current: License, updates: Partial<License>, resetPortalPassword = false) {
        try {
            const next = { ...current, ...updates };

            await apiPost("/api/admin/licenses/update", {
                id: current.id,
                status: next.status,
                expiresAt: next.expires_at,
                boundUuid: next.bound_uuid ?? null,
                discordName: next.discord_name ?? null,
                productTier: next.product_tier,
                resetPortalPassword
            });

            setRows((prev) =>
                prev.map((row) => (row.id === current.id ? { ...next, portal_password_set_at: resetPortalPassword ? null : next.portal_password_set_at } : row))
            );
        } catch (e) {
            alert(e instanceof Error ? e.message : "Update failed");
        }
    }

    async function deleteLicense(id: number) {
        const confirmed = window.confirm("Delete this license?");
        if (!confirmed) return;

        try {
            await apiPost("/api/admin/licenses/delete", { id });
            setRows((prev) => prev.filter((row) => row.id !== id));
        } catch (e) {
            alert(e instanceof Error ? e.message : "Delete failed");
        }
    }

    function toggleStatus(row: License) {
        const newStatus = row.status === "revoked" ? "active" : "revoked";
        updateLicense(row, { status: newStatus });
    }

    function clearUuid(row: License) {
        updateLicense(row, { bound_uuid: null });
    }

    function extend30Days(row: License) {
        if (row.expires_at === "never") {
            const newDate = new Date();
            newDate.setDate(newDate.getDate() + 30);
            updateLicense(row, { expires_at: newDate.toISOString() });
            return;
        }

        const newDate = new Date(row.expires_at);
        newDate.setDate(newDate.getDate() + 30);
        updateLicense(row, { expires_at: newDate.toISOString() });
    }

    function resetPortalPassword(row: License) {
        const confirmed = window.confirm("Reset the portal password for this license?");
        if (!confirmed) return;
        updateLicense(row, {}, true);
    }

    function downloadLicenseConfig(row: License) {
        const config = {
            licenseKey: row.license_key,
            discordName: row.discord_name
        };

        const json = JSON.stringify(config, null, 2);
        const blob = new Blob([json], { type: "application/json" });
        const url = URL.createObjectURL(blob);

        const a = document.createElement("a");
        a.href = url;
        a.download = "larpclient-license.json";
        document.body.appendChild(a);
        a.click();
        a.remove();

        URL.revokeObjectURL(url);
    }

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        if (!q) return rows;

        return rows.filter((row) =>
            row.license_key.toLowerCase().includes(q) ||
            (row.bound_uuid || "").toLowerCase().includes(q) ||
            (row.discord_name || "").toLowerCase().includes(q) ||
            (row.last_seen_name || "").toLowerCase().includes(q) ||
            row.status.toLowerCase().includes(q) ||
            row.product_tier.toLowerCase().includes(q) ||
            (row.last_seen_client_type || "").toLowerCase().includes(q)
        );
    }, [rows, search]);

    return (
        <motion.div className="glass card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 18, gap: 12, alignItems: "center" }}>
                <div>
                    <h1 className="card-title">Licenses</h1>
                    <div className="muted">Manage keys, Discord identities, UUID bindings, expiry, and portal-password resets.</div>
                </div>
                <div style={{ display: "flex", gap: 12 }}>
                    <input
                        className="input"
                        placeholder="Search keys, Discord, uuid, user..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        style={{ minWidth: 320 }}
                    />
                    <button className="button" onClick={load}>Refresh</button>
                </div>
            </div>

            {loading && <div className="muted">Loading...</div>}
            {error && <div style={{ color: "#ff8bcf" }}>{error}</div>}

            {!loading && !error && (
                <table className="table">
                    <thead>
                    <tr>
                        <th>Key</th>
                        <th>Discord</th>
                        <th>UUID</th>
                        <th>User</th>
                        <th>Tier</th>
                        <th>Portal Password</th>
                        <th>Client</th>
                        <th>Status</th>
                        <th>Expires</th>
                        <th>Actions</th>
                    </tr>
                    </thead>

                    <tbody>
                    {filtered.map((row) => (
                        <tr key={row.id}>
                            <td>{row.license_key}</td>
                            <td style={{ minWidth: 220 }}>
                                <input
                                    className="input"
                                    value={row.discord_name || ""}
                                    onChange={(e) => {
                                        const value = e.target.value;
                                        setRows((prev) => prev.map((item) => (item.id === row.id ? { ...item, discord_name: value } : item)));
                                    }}
                                    onBlur={(e) => {
                                        const value = e.target.value.trim();
                                        if (value !== (row.discord_name || "")) {
                                            updateLicense(row, { discord_name: value });
                                        }
                                    }}
                                />
                            </td>
                            <td>{row.bound_uuid || "—"}</td>
                            <td>{row.last_seen_name || "—"}</td>
                            <td>
                                <select
                                    className="select"
                                    value={row.product_tier}
                                    onChange={(e) => updateLicense(row, { product_tier: e.target.value })}
                                >
                                    <option value="addon">addon</option>
                                    <option value="mod">mod</option>
                                </select>
                            </td>
                            <td>
                                <span className={`badge ${row.portal_password_set_at ? "" : "revoked"}`}>
                                    {row.portal_password_set_at ? "set" : "not set"}
                                </span>
                            </td>
                            <td>{row.last_seen_client_type || "—"}</td>
                            <td>
                                <span className={`badge ${row.status === "revoked" ? "revoked" : ""}`}>
                                    {row.status}
                                </span>
                            </td>
                            <td>{row.expires_at === "never" ? "Never" : new Date(row.expires_at).toLocaleString()}</td>
                            <td>
                                <div className="actions">
                                    <button className="button" onClick={() => toggleStatus(row)}>
                                        {row.status === "revoked" ? "Activate" : "Revoke"}
                                    </button>

                                    <button className="button" onClick={() => clearUuid(row)}>
                                        Clear UUID
                                    </button>

                                    <button className="button" onClick={() => extend30Days(row)}>
                                        +30d
                                    </button>

                                    <button className="button" onClick={() => resetPortalPassword(row)}>
                                        Reset Password
                                    </button>

                                    <button className="button" onClick={() => downloadLicenseConfig(row)}>
                                        Download JSON
                                    </button>

                                    <button className="button danger" onClick={() => deleteLicense(row.id)}>
                                        Delete
                                    </button>
                                </div>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}
        </motion.div>
    );
}
