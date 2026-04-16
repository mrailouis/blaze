import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { apiGet, apiPost } from "../lib/api";

type PlayerOverrideRecord = {
    id: number;
    uuid: string;
    minecraftName: string | null;
    customName: string | null;
    scaleX: number;
    scaleY: number;
    scaleZ: number;
    enabled: boolean;
    lastResolvedAt: string | null;
    updatedAt: string;
};

type LookupResult = {
    uuid: string;
    minecraftName: string | null;
    source: string;
};

type FormState = {
    uuid: string;
    minecraftName: string;
    customName: string;
    scaleX: string;
    scaleY: string;
    scaleZ: string;
    enabled: boolean;
};

const EMPTY_FORM: FormState = {
    uuid: "",
    minecraftName: "",
    customName: "",
    scaleX: "1",
    scaleY: "1",
    scaleZ: "1",
    enabled: true
};

export default function PlayerOverridesPage() {
    const [rows, setRows] = useState<PlayerOverrideRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [lookupLoading, setLookupLoading] = useState(false);
    const [error, setError] = useState("");
    const [lookup, setLookup] = useState<LookupResult | null>(null);
    const [form, setForm] = useState<FormState>(EMPTY_FORM);

    async function load() {
        try {
            setLoading(true);
            setError("");
            const data = await apiGet<{ ok: true; overrides: PlayerOverrideRecord[] }>("/api/admin/player-overrides/list");
            setRows(data.overrides);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load overrides");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        load();
    }, []);

    async function lookupUuid() {
        if (!form.uuid.trim()) {
            setError("Enter a UUID first.");
            return;
        }

        try {
            setLookupLoading(true);
            setError("");
            const data = await apiPost<{ ok: true; uuid: string; minecraftName: string | null; source: string }>(
                "/api/admin/player-overrides/lookup",
                { uuid: form.uuid }
            );

            setLookup(data);
            setForm((prev) => ({
                ...prev,
                uuid: data.uuid,
                minecraftName: data.minecraftName || prev.minecraftName
            }));
        } catch (e) {
            setLookup(null);
            setError(e instanceof Error ? e.message : "Lookup failed");
        } finally {
            setLookupLoading(false);
        }
    }

    async function saveOverride() {
        if (!form.uuid.trim()) {
            setError("UUID is required.");
            return;
        }

        try {
            setSaving(true);
            setError("");
            const data = await apiPost<{ ok: true; override: PlayerOverrideRecord | null }>(
                "/api/admin/player-overrides/upsert",
                {
                    uuid: form.uuid,
                    customName: form.customName,
                    scaleX: Number(form.scaleX),
                    scaleY: Number(form.scaleY),
                    scaleZ: Number(form.scaleZ),
                    enabled: form.enabled
                }
            );

            if (data.override) {
                const savedOverride = data.override;

                setRows((prev) => {
                    const withoutExisting = prev.filter((row) => row.id !== savedOverride.id);
                    return [savedOverride, ...withoutExisting];
                });

                setLookup({
                    uuid: savedOverride.uuid,
                    minecraftName: savedOverride.minecraftName,
                    source: lookup?.source ?? "saved"
                });

                setForm({
                    uuid: savedOverride.uuid,
                    minecraftName: savedOverride.minecraftName || "",
                    customName: savedOverride.customName || "",
                    scaleX: String(savedOverride.scaleX),
                    scaleY: String(savedOverride.scaleY),
                    scaleZ: String(savedOverride.scaleZ),
                    enabled: savedOverride.enabled
                });
            }
        } catch (e) {
            setError(e instanceof Error ? e.message : "Save failed");
        } finally {
            setSaving(false);
        }
    }

    async function deleteOverride(row: PlayerOverrideRecord) {
        const confirmed = window.confirm(`Delete the override for ${row.minecraftName || row.uuid}?`);
        if (!confirmed) return;

        try {
            await apiPost("/api/admin/player-overrides/delete", { id: row.id });
            setRows((prev) => prev.filter((item) => item.id !== row.id));

            if (form.uuid === row.uuid) {
                clearForm();
            }
        } catch (e) {
            setError(e instanceof Error ? e.message : "Delete failed");
        }
    }

    function clearForm() {
        setForm(EMPTY_FORM);
        setLookup(null);
        setError("");
    }

    function loadIntoForm(row: PlayerOverrideRecord) {
        setLookup({
            uuid: row.uuid,
            minecraftName: row.minecraftName,
            source: "saved"
        });
        setForm({
            uuid: row.uuid,
            minecraftName: row.minecraftName || "",
            customName: row.customName || "",
            scaleX: String(row.scaleX),
            scaleY: String(row.scaleY),
            scaleZ: String(row.scaleZ),
            enabled: row.enabled
        });
    }

    return (
        <div className="grid">
            <motion.div className="glass card col-4" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div className="card-title">Override Builder</div>
                <div className="muted" style={{ marginBottom: 16 }}>
                    Resolve any UUID, review the current IGN, then set a formatted name and local-only model scales for all Larp clients.
                </div>

                <label className="label">Target UUID</label>
                <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
                    <input
                        className="input"
                        placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                        value={form.uuid}
                        onChange={(e) => setForm((prev) => ({ ...prev, uuid: e.target.value }))}
                    />
                    <button className="button" onClick={lookupUuid} disabled={lookupLoading}>
                        {lookupLoading ? "Looking..." : "Lookup"}
                    </button>
                </div>

                <div style={{ display: "grid", gap: 12 }}>
                    <div>
                        <label className="label">Current IGN</label>
                        <input className="input" value={form.minecraftName} readOnly placeholder="Lookup or save a UUID first" />
                    </div>

                    <div>
                        <label className="label">Custom Name</label>
                        <input
                            className="input"
                            placeholder="&bCreator &7(&fMrai&7)"
                            value={form.customName}
                            onChange={(e) => setForm((prev) => ({ ...prev, customName: e.target.value }))}
                        />
                        <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
                            Supports `&` and `§` legacy formatting codes.
                        </div>
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10 }}>
                        <div>
                            <label className="label">Scale X</label>
                            <input
                                className="input"
                                type="number"
                                min="0.1"
                                max="8"
                                step="0.1"
                                value={form.scaleX}
                                onChange={(e) => setForm((prev) => ({ ...prev, scaleX: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="label">Scale Y</label>
                            <input
                                className="input"
                                type="number"
                                min="0.1"
                                max="8"
                                step="0.1"
                                value={form.scaleY}
                                onChange={(e) => setForm((prev) => ({ ...prev, scaleY: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="label">Scale Z</label>
                            <input
                                className="input"
                                type="number"
                                min="0.1"
                                max="8"
                                step="0.1"
                                value={form.scaleZ}
                                onChange={(e) => setForm((prev) => ({ ...prev, scaleZ: e.target.value }))}
                            />
                        </div>
                    </div>

                    <label style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <input
                            type="checkbox"
                            checked={form.enabled}
                            onChange={(e) => setForm((prev) => ({ ...prev, enabled: e.target.checked }))}
                        />
                        <span>Enabled</span>
                    </label>

                    {lookup && (
                        <div className="glass" style={{ padding: 14, borderRadius: 18 }}>
                            <div style={{ fontWeight: 700 }}>{lookup.minecraftName || "IGN not resolved yet"}</div>
                            <div className="muted" style={{ fontSize: 12 }}>{lookup.uuid}</div>
                            <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>Source: {lookup.source}</div>
                        </div>
                    )}

                    {error && <div style={{ color: "#ff8bcf" }}>{error}</div>}

                    <div style={{ display: "flex", gap: 10 }}>
                        <button className="button success" onClick={saveOverride} disabled={saving}>
                            {saving ? "Saving..." : "Save Override"}
                        </button>
                        <button className="button" onClick={clearForm}>Clear</button>
                    </div>

                    <div className="muted" style={{ fontSize: 12 }}>
                        Changes propagate through the shared heartbeat and usually land in-game within about 10 seconds.
                    </div>
                </div>
            </motion.div>

            <motion.div className="glass card col-8" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 18 }}>
                    <div>
                        <div className="card-title">Saved Overrides</div>
                        <div className="muted">Shared by the legit client and the addon. Visibility stays local to people running Larp.</div>
                    </div>
                    <button className="button" onClick={load}>Refresh</button>
                </div>

                {loading && <div className="muted">Loading...</div>}

                {!loading && rows.length === 0 && (
                    <div className="muted">No overrides saved yet.</div>
                )}

                {!loading && rows.length > 0 && (
                    <table className="table">
                        <thead>
                        <tr>
                            <th>Player</th>
                            <th>Custom Name</th>
                            <th>Scale</th>
                            <th>Status</th>
                            <th>Resolved</th>
                            <th>Actions</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((row) => (
                            <tr key={row.id}>
                                <td>
                                    <div style={{ fontWeight: 700 }}>{row.minecraftName || "Unknown IGN"}</div>
                                    <div className="muted" style={{ fontSize: 12 }}>{row.uuid}</div>
                                </td>
                                <td>
                                    <div style={{ whiteSpace: "pre-wrap" }}>{row.customName || "—"}</div>
                                </td>
                                <td>{row.scaleX} / {row.scaleY} / {row.scaleZ}</td>
                                <td>
                                    <span className={`badge ${row.enabled ? "" : "revoked"}`}>
                                        {row.enabled ? "enabled" : "disabled"}
                                    </span>
                                </td>
                                <td>{row.lastResolvedAt ? new Date(row.lastResolvedAt).toLocaleString() : "—"}</td>
                                <td>
                                    <div className="actions">
                                        <button className="button" onClick={() => loadIntoForm(row)}>Edit</button>
                                        <button className="button danger" onClick={() => deleteOverride(row)}>Delete</button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </motion.div>
        </div>
    );
}
