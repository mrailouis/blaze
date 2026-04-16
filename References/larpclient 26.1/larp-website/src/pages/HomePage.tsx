import { motion } from "framer-motion";
import { BookOpen, Code2, LogIn, ShieldCheck, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import DocumentTree from "../components/DocumentTree";
import MarkdownPreview from "../components/MarkdownPreview";
import SiteFrame from "../components/SiteFrame";
import { getBootstrap } from "../lib/api";
import type { BootstrapData } from "../types";

export default function HomePage() {
    const [data, setData] = useState<BootstrapData | null>(null);
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelled = false;

        async function load() {
            try {
                setError("");
                const result = await getBootstrap();
                if (!cancelled) {
                    setData(result);
                }
            } catch (loadError) {
                if (!cancelled) {
                    setError(loadError instanceof Error ? loadError.message : "Failed to load site");
                }
            }
        }

        load();
        const interval = window.setInterval(load, 10_000);
        return () => {
            cancelled = true;
            window.clearInterval(interval);
        };
    }, []);

    if (error) {
        return <div className="standalone-wrap"><div className="surface empty-panel">{error}</div></div>;
    }

    if (!data) {
        return <div className="standalone-wrap"><div className="surface empty-panel">Loading...</div></div>;
    }

    const sourceCodeUrl = data.settings.source_code_url || "https://github.com/mrailouis/larpclient-public";

    return (
        <SiteFrame session={data.session} settings={data.settings}>
            <section className="hero-grid">
                <motion.article className="surface hero-panel" initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }}>
                    <span className="eyebrow">Legit-first Minecraft utility</span>
                    <h1>LarpClient keeps the public side clean and puts the private automation set behind a proper addon access flow.</h1>
                    <p className="hero-copy">
                        The public wiki follows the legit sidebar. The addon area is separate, requires a valid addon license,
                        checks the stored Discord name, and uses a portal password created on first sign-in.
                    </p>
                    <div className="action-row">
                        <Link className="button accent" to="/wiki">
                            <BookOpen size={16} />
                            Open legit wiki
                        </Link>
                        <Link className="button" to="/login?mode=addon">
                            <Sparkles size={16} />
                            Open addons
                        </Link>
                        <a className="button" href={sourceCodeUrl} target="_blank" rel="noreferrer">
                            <Code2 size={16} />
                            Public source
                        </a>
                    </div>
                </motion.article>

                <motion.aside className="surface status-stack" initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="metric-card">
                        <span className="eyebrow">Live heartbeat</span>
                        <strong>{data.onlineTotals.total}</strong>
                        <p>{data.onlineTotals.mod} on legit, {data.onlineTotals.addon} on addon.</p>
                    </div>
                    <div className="metric-card">
                        <span className="eyebrow">Addon access</span>
                        <strong>License + Discord + password</strong>
                        <p>The site re-checks those values on private requests instead of trusting a one-time key entry.</p>
                    </div>
                </motion.aside>
            </section>

            <section className="split-grid">
                <motion.article className="surface content-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Public overview</span>
                            <h2>{data.publicHome?.title || "LarpClient"}</h2>
                        </div>
                        <span className="badge neutral">Legit side</span>
                    </div>
                    <MarkdownPreview markdown={data.publicHome?.body_markdown || "No public home page published yet."} />
                </motion.article>

                <motion.aside className="surface content-panel" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Wiki map</span>
                            <h2>Current legit sections</h2>
                        </div>
                        <Link className="inline-link" to="/wiki">Browse all</Link>
                    </div>
                    <DocumentTree documents={data.publicWiki} basePath="/wiki" emptyLabel="The legit wiki is still empty." />
                </motion.aside>
            </section>

            <section className="split-grid">
                <motion.article className="surface content-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Private addon area</span>
                            <h2>Separate on purpose</h2>
                        </div>
                        <span className="badge neutral">Licensed only</span>
                    </div>
                    <div className="bullet-stack">
                        <div className="bullet-row">
                            <ShieldCheck size={15} />
                            <span>The addon stays behind a license record that includes both the product tier and the Discord name tied to that user.</span>
                        </div>
                        <div className="bullet-row">
                            <LogIn size={15} />
                            <span>First site login creates the portal password. Later logins reuse it instead of exposing the private pages with a raw key alone.</span>
                        </div>
                        <div className="bullet-row">
                            <Sparkles size={15} />
                            <span>The addon wiki only documents the current addon-only modules and addon-only automation settings.</span>
                        </div>
                    </div>
                    <div className="action-row">
                        <Link className="button accent" to="/login?mode=addon">Addon sign-in</Link>
                        <Link className="button" to="/addons">Go to addon area</Link>
                    </div>
                </motion.article>

                <motion.article className="surface content-panel" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Start here</span>
                            <h2>Useful entry points</h2>
                        </div>
                    </div>
                    <div className="link-list">
                        <Link className="link-list-item" to="/wiki/public-skyblock-general">
                            <strong>Skyblock General</strong>
                            <span>Skyblock QoL including lag detection, mute cleanup, menus, slot locking, and Visual FME.</span>
                        </Link>
                        <Link className="link-list-item" to="/wiki/public-floor7-predev">
                            <strong>Floor 7 Predev</strong>
                            <span>Arrow Align and Lights Device live here on the legit side as solver-focused utilities.</span>
                        </Link>
                        <Link className="link-list-item" to="/wiki/public-kuudra-phase-1">
                            <strong>Kuudra Phase 1</strong>
                            <span>Kuudra waypoints and pearl prediction are grouped together for public route guidance.</span>
                        </Link>
                    </div>
                </motion.article>
            </section>

            {data.publicVideos.length > 0 && (
                <section className="video-grid">
                    {data.publicVideos.map((video) => (
                        <motion.article key={video.id} className="surface video-card" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                            <div className="section-heading">
                                <h2>{video.title}</h2>
                                <span className="badge neutral">Streaming only</span>
                            </div>
                            <video
                                className="video-player"
                                src={video.streamUrl}
                                controls
                                controlsList="nodownload"
                                disablePictureInPicture
                                preload="metadata"
                            />
                            {video.description ? <p className="muted-text">{video.description}</p> : null}
                        </motion.article>
                    ))}
                </section>
            )}
        </SiteFrame>
    );
}
