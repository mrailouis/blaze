import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import DocumentTree from "../components/DocumentTree";
import SiteFrame from "../components/SiteFrame";
import MarkdownPreview from "../components/MarkdownPreview";
import { getBootstrap } from "../lib/api";
import type { BootstrapData } from "../types";

export default function PortalPage() {
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
            } catch (e) {
                if (!cancelled) {
                    setError(e instanceof Error ? e.message : "Failed to load addon area");
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

    if (!data || !data.addonHome) {
        return <div className="standalone-wrap"><div className="surface empty-panel">Loading...</div></div>;
    }

    return (
        <SiteFrame session={data.session} settings={data.settings}>
            <section className="hero-grid">
                <motion.div className="surface hero-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <span className="eyebrow">Licensed addon area</span>
                    <h1>{data.addonHome.title}</h1>
                    <p className="hero-copy">{data.addonHome.excerpt}</p>
                    <div className="action-row">
                        <Link className="button accent" to="/addons/wiki">Addon wiki</Link>
                        <a className="button" href={data.settings.discord_url || "https://discord.gg/replace-me"} target="_blank" rel="noreferrer">Discord</a>
                    </div>
                </motion.div>

                <motion.aside className="surface status-stack" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="metric-card">
                        <span className="eyebrow">Signed in as</span>
                        <strong>{data.session?.username || data.session?.discordName || "licensed user"}</strong>
                        <p>Every private request revalidates the addon license in D1 before serving content.</p>
                    </div>
                    <div className="metric-card">
                        <span className="eyebrow">Private scope</span>
                        <strong>{data.addonWiki.length} addon articles</strong>
                        <p>{data.addonVideos.length} private videos, plus a separate route tree for addon-only modules and settings.</p>
                    </div>
                </motion.aside>
            </section>

            <section className="split-grid">
                <motion.div className="surface content-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">What is inside</span>
                            <h2>Addon overview</h2>
                        </div>
                        <span className="badge neutral">Private content</span>
                    </div>
                    <MarkdownPreview markdown={data.addonHome.body_markdown} />
                </motion.div>

                <motion.div className="surface content-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Private wiki</span>
                            <h2>Section map</h2>
                        </div>
                        <Link className="inline-link" to="/addons/wiki">See all</Link>
                    </div>
                    <DocumentTree documents={data.addonWiki} basePath="/addons/wiki" emptyLabel="No addon wiki pages published yet." />
                </motion.div>
            </section>

            <section className="split-grid">
                <motion.article className="surface content-panel" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Command lane</span>
                            <h2>What the addon extends</h2>
                        </div>
                    </div>
                    <div className="bullet-stack">
                        <div className="bullet-row"><span className="doc-tree-prefix">-</span><span>Addon access expands `.larp` with private automation controls such as velocity buffer, blink routes, preroute editing, and Cow Hat zone tools.</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">-</span><span>The addon also registers `.p3` for the private Phase 3 route and ring toolset.</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">-</span><span>The addon wiki focuses on shipped private modules and private-only behavior, not the public legit setup path.</span></div>
                    </div>
                </motion.article>

                <motion.article className="surface content-panel" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">Portal rules</span>
                            <h2>Access model</h2>
                        </div>
                    </div>
                    <div className="bullet-stack">
                        <div className="bullet-row"><span className="doc-tree-prefix">1</span><span>The site checks the license key, stored Discord name, status, expiry, and product tier before every private request.</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">2</span><span>First site login sets the portal password on that license. Admin can reset it later from the auth panel.</span></div>
                        <div className="bullet-row"><span className="doc-tree-prefix">3</span><span>Deleting, revoking, or changing the license immediately removes access because the session is not trusted on its own.</span></div>
                    </div>
                </motion.article>
            </section>

            {data.addonVideos.length > 0 && (
                <section className="video-grid">
                    {data.addonVideos.map((video) => (
                        <motion.article key={video.id} className="surface video-card" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                            <div className="section-heading">
                                <h2>{video.title}</h2>
                                <span className="badge neutral">Licensed stream</span>
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
