import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { apiPost } from "../lib/api";

function makeKey() {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    const part = () => Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
    return `LARP-${part()}-${part()}-${part()}`;
}

type ExpiryPreset = "never" | "1d" | "1w" | "1m" | "1y" | "2y" | "custom";

function presetToIso(preset: ExpiryPreset, customValue: string): string {
    if (preset === "never") return "never";
    if (preset === "custom") return new Date(customValue).toISOString();

    const d = new Date();
    if (preset === "1d") d.setDate(d.getDate() + 1);
    if (preset === "1w") d.setDate(d.getDate() + 7);
    if (preset === "1m") d.setMonth(d.getMonth() + 1);
    if (preset === "1y") d.setFullYear(d.getFullYear() + 1);
    if (preset === "2y") d.setFullYear(d.getFullYear() + 2);

    return d.toISOString();
}

export default function NewLicensePage() {
    const [licenseKey, setLicenseKey] = useState(makeKey());
    const [discordName, setDiscordName] = useState("");
    const [status, setStatus] = useState("active");
    const [productTier, setProductTier] = useState("addon");
    const [boundUuid, setBoundUuid] = useState("");
    const [expiryPreset, setExpiryPreset] = useState<ExpiryPreset>("never");
    const [customExpiresAt, setCustomExpiresAt] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    const resolvedExpiry = useMemo(() => {
        if (expiryPreset === "custom" && !customExpiresAt) return "";
        return presetToIso(expiryPreset, customExpiresAt);
    }, [expiryPreset, customExpiresAt]);

    async function handleCreate() {
        try {
            setError("");
            setMessage("");

            if (!discordName.trim()) {
                setError("Discord name is required.");
                return;
            }

            if (!resolvedExpiry) {
                setError("Please choose a custom expiry date.");
                return;
            }

            await apiPost("/api/admin/licenses/create", {
                licenseKey,
                discordName,
                boundUuid: boundUuid || null,
                status,
                expiresAt: resolvedExpiry,
                productTier
            });

            setMessage("License created.");
            setLicenseKey(makeKey());
            setDiscordName("");
            setBoundUuid("");
            setExpiryPreset("never");
            setCustomExpiresAt("");
        } catch (e) {
            setError(e instanceof Error ? e.message : "Create failed");
        }
    }

    return (
        <motion.div className="glass card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
            <h1 className="card-title">Create License</h1>
            <p className="muted">New addon access now ties the site login to a license, Discord name, and portal password.</p>

            <div className="grid" style={{ marginTop: 18 }}>
                <div className="col-6">
                    <label className="label">License Key</label>
                    <input className="input" value={licenseKey} onChange={(e) => setLicenseKey(e.target.value)} />
                </div>

                <div className="col-6">
                    <label className="label">Discord Name</label>
                    <input className="input" placeholder="username or username#0000" value={discordName} onChange={(e) => setDiscordName(e.target.value)} />
                </div>

                <div className="col-6">
                    <label className="label">Status</label>
                    <select className="select" value={status} onChange={(e) => setStatus(e.target.value)}>
                        <option value="active">active</option>
                        <option value="revoked">revoked</option>
                    </select>
                </div>

                <div className="col-6">
                    <label className="label">Bound UUID</label>
                    <input className="input" placeholder="Optional" value={boundUuid} onChange={(e) => setBoundUuid(e.target.value)} />
                </div>

                <div className="col-6">
                    <label className="label">Tier</label>
                    <select className="select" value={productTier} onChange={(e) => setProductTier(e.target.value)}>
                        <option value="addon">addon</option>
                        <option value="mod">mod</option>
                    </select>
                </div>

                <div className="col-6">
                    <label className="label">Expires</label>
                    <select className="select" value={expiryPreset} onChange={(e) => setExpiryPreset(e.target.value as ExpiryPreset)}>
                        <option value="never">Never</option>
                        <option value="1d">1 Day</option>
                        <option value="1w">1 Week</option>
                        <option value="1m">1 Month</option>
                        <option value="1y">1 Year</option>
                        <option value="2y">2 Years</option>
                        <option value="custom">Custom</option>
                    </select>
                </div>

                {expiryPreset === "custom" && (
                    <div className="col-12">
                        <label className="label">Custom Expiry</label>
                        <input
                            className="input"
                            type="datetime-local"
                            value={customExpiresAt}
                            onChange={(e) => setCustomExpiresAt(e.target.value)}
                        />
                    </div>
                )}

                {message && <div className="col-12" style={{ color: "#8fffd9" }}>{message}</div>}
                {error && <div className="col-12" style={{ color: "#ff8bcf" }}>{error}</div>}

                <div className="col-12" style={{ display: "flex", gap: 12 }}>
                    <button className="button" onClick={handleCreate}>Create License</button>
                    <button className="button" onClick={() => setLicenseKey(makeKey())}>
                        Generate Key
                    </button>
                </div>
            </div>
        </motion.div>
    );
}
