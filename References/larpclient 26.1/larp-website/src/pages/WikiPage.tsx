import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import DocumentTree from "../components/DocumentTree";
import MarkdownPreview from "../components/MarkdownPreview";
import SiteFrame from "../components/SiteFrame";
import { getBootstrap } from "../lib/api";
import { getDocumentTaxonomyLabel } from "../lib/documentGroups";
import type { BootstrapData } from "../types";

export default function WikiPage({ audience }: { audience: "public" | "addon" }) {
    const [data, setData] = useState<BootstrapData | null>(null);
    const [error, setError] = useState("");
    const params = useParams();

    useEffect(() => {
        let cancelled = false;

        getBootstrap()
            .then((result) => {
                if (!cancelled) {
                    setData(result);
                }
            })
            .catch((loadError) => {
                if (!cancelled) {
                    setError(loadError instanceof Error ? loadError.message : "Failed to load wiki");
                }
            });

        return () => {
            cancelled = true;
        };
    }, []);

    if (error) {
        return <div className="standalone-wrap"><div className="surface empty-panel">{error}</div></div>;
    }

    if (!data) {
        return <div className="standalone-wrap"><div className="surface empty-panel">Loading...</div></div>;
    }

    const docs = audience === "addon" ? data.addonWiki : data.publicWiki;
    const selected = docs.find((doc) => doc.slug === params.slug) ?? docs[0] ?? null;
    const basePath = audience === "addon" ? "/addons/wiki" : "/wiki";

    return (
        <SiteFrame session={data.session} settings={data.settings}>
            <section className="wiki-shell">
                <aside className="surface wiki-rail">
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">{audience === "addon" ? "Private docs" : "Public docs"}</span>
                            <h2>{audience === "addon" ? "Addon Wiki" : "Legit Wiki"}</h2>
                        </div>
                        <span className="badge neutral">{docs.length} pages</span>
                    </div>
                    <DocumentTree documents={docs} basePath={basePath} selectedSlug={selected?.slug} emptyLabel="No wiki pages published yet." />
                </aside>

                <motion.article className="surface wiki-document" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
                    {selected ? (
                        <>
                            <div className="document-header">
                                <div>
                                    <span className="eyebrow">{selected.audience === "addon" ? "Addon section" : "Legit section"}</span>
                                    <h1>{selected.title}</h1>
                                </div>
                                <span className="badge neutral">{getDocumentTaxonomyLabel(selected)}</span>
                            </div>
                            {selected.excerpt ? <p className="document-excerpt">{selected.excerpt}</p> : null}
                            <MarkdownPreview markdown={selected.body_markdown} />
                        </>
                    ) : (
                        <div className="empty-state">There are no pages in this wiki yet.</div>
                    )}
                </motion.article>
            </section>
        </SiteFrame>
    );
}
