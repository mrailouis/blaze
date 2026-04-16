import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { apiPost, getLicenseStatus, getSession } from "../lib/api";

type Mode = "license" | "admin";

export default function LoginPage() {
    const [searchParams] = useSearchParams();
    const [mode, setMode] = useState<Mode>(searchParams.get("mode") === "admin" ? "admin" : "license");
    const [licenseKey, setLicenseKey] = useState("");
    const [discordName, setDiscordName] = useState("");
    const [passwordSet, setPasswordSet] = useState<boolean | null>(null);
    const [statusMessage, setStatusMessage] = useState("");
    const [username, setUsername] = useState("mrai");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        getSession().then((session) => {
            if (session?.role === "admin") {
                navigate("/admin", { replace: true });
            } else if (session?.productTier === "addon") {
                navigate("/addons", { replace: true });
            }
        });
    }, [navigate]);

    async function handleLookup() {
        try {
            setLoading(true);
            setError("");
            setStatusMessage("");

            const result = await getLicenseStatus(licenseKey, discordName);
            setPasswordSet(result.passwordSet);
            setStatusMessage(
                result.passwordSet
                    ? "Portal password already exists for this license. Enter it to continue."
                    : "First site login for this license. Create a portal password to unlock the addon area."
            );
        } catch (e) {
            setPasswordSet(null);
            setError(e instanceof Error ? e.message : "License check failed");
        } finally {
            setLoading(false);
        }
    }

    async function handleSubmit() {
        try {
            setLoading(true);
            setError("");
            setStatusMessage("");

            if (mode === "license") {
                if (passwordSet === null) {
                    await handleLookup();
                    return;
                }

                await apiPost("/api/auth/license-login", { licenseKey, discordName, password });
                navigate("/addons", { replace: true });
                return;
            }

            await apiPost("/api/auth/admin-login", { username, password });
            navigate("/admin", { replace: true });
        } catch (e) {
            setError(e instanceof Error ? e.message : "Login failed");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="standalone-wrap">
            <motion.section className="login-shell" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                <article className="surface login-info-panel">
                    <span className="eyebrow">LarpClient access</span>
                    <h1>Private addon pages now use a real account flow instead of a bare key check.</h1>
                    <p className="hero-copy">
                        Addon users enter the license key and the Discord name stored on that license. On the first site login,
                        they set a portal password. After that, the same password unlocks the addon area.
                    </p>
                    <div className="bullet-stack">
                        <div className="bullet-row"><span className="doc-tree-prefix">1</span><span>Valid addon license</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">2</span><span>Matching Discord name from the auth database</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">3</span><span>Portal password created on first login</span></div>
                    </div>
                </article>

                <article className="surface login-form-panel">
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Sign in</span>
                            <h2>{mode === "license" ? "Addon access" : "Admin access"}</h2>
                        </div>
                        <span className="badge neutral">{mode === "license" ? "licensed" : "root"}</span>
                    </div>

                    <div className="tab-row">
                        <button
                            className={`button ${mode === "license" ? "accent" : ""}`}
                            onClick={() => {
                                setMode("license");
                                setError("");
                            }}
                        >
                            Addon login
                        </button>
                        <button
                            className={`button ${mode === "admin" ? "accent" : ""}`}
                            onClick={() => {
                                setMode("admin");
                                setError("");
                            }}
                        >
                            Admin login
                        </button>
                    </div>

                    {mode === "license" ? (
                        <>
                            <div className="field-stack">
                                <label className="label">License key</label>
                                <input
                                    className="input"
                                    placeholder="LARP-XXXX-XXXX-XXXX"
                                    value={licenseKey}
                                    onChange={(e) => {
                                        setLicenseKey(e.target.value);
                                        setPasswordSet(null);
                                        setStatusMessage("");
                                    }}
                                />
                            </div>

                            <div className="field-stack">
                                <label className="label">Discord name</label>
                                <input
                                    className="input"
                                    placeholder="username or username#0000"
                                    value={discordName}
                                    onChange={(e) => {
                                        setDiscordName(e.target.value);
                                        setPasswordSet(null);
                                        setStatusMessage("");
                                    }}
                                />
                            </div>

                            {passwordSet !== null ? (
                                <div className="field-stack">
                                    <label className="label">{passwordSet ? "Portal password" : "Create portal password"}</label>
                                    <input
                                        className="input"
                                        type="password"
                                        placeholder={passwordSet ? "Enter your portal password" : "Create a new password"}
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter") handleSubmit();
                                        }}
                                    />
                                </div>
                            ) : null}

                            {statusMessage ? <div className="success-text">{statusMessage}</div> : null}
                        </>
                    ) : (
                        <>
                            <div className="field-stack">
                                <label className="label">Username</label>
                                <input
                                    className="input"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") handleSubmit();
                                    }}
                                />
                            </div>
                            <div className="field-stack">
                                <label className="label">Password</label>
                                <input
                                    className="input"
                                    type="password"
                                    placeholder="••••••••"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") handleSubmit();
                                    }}
                                />
                            </div>
                        </>
                    )}

                    {error ? <div className="error-text">{error}</div> : null}

                    <div className="action-row">
                        {mode === "license" ? (
                            <>
                                <button className="button" onClick={handleLookup} disabled={loading}>
                                    {loading ? "Checking..." : "Check license"}
                                </button>
                                <button className="button accent" onClick={handleSubmit} disabled={loading || passwordSet === null}>
                                    {loading ? "Opening..." : passwordSet ? "Open addon area" : "Set password and open"}
                                </button>
                            </>
                        ) : (
                            <button className="button accent full-width" onClick={handleSubmit} disabled={loading}>
                                {loading ? "Signing in..." : "Open admin"}
                            </button>
                        )}
                    </div>
                </article>
            </motion.section>
        </div>
    );
}
