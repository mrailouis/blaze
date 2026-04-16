import { motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import MarkdownPreview from "../components/MarkdownPreview";
import SiteFrame from "../components/SiteFrame";
import { apiDelete, apiForm, apiGet, apiPost, getBootstrap } from "../lib/api";
import { buildDocumentGroups, getDocumentTaxonomyLabel } from "../lib/documentGroups";
import type { BootstrapData, DocumentsResponse, SettingsResponse, SiteDocument, SiteVideo, VideosResponse } from "../types";

type EditorState = {
    id?: number;
    slug: string;
    title: string;
    excerpt: string;
    kind: "page" | "wiki";
    audience: "public" | "addon";
    category: string;
    subcategory: string;
    sort_order: number;
    body_markdown: string;
};

type AdminSection = "content" | "links" | "media";

const emptyEditor: EditorState = {
    slug: "",
    title: "",
    excerpt: "",
    kind: "wiki",
    audience: "public",
    category: "",
    subcategory: "",
    sort_order: 0,
    body_markdown: "# New page\n"
};

function fromDocument(doc: SiteDocument): EditorState {
    return {
        id: doc.id,
        slug: doc.slug,
        title: doc.title,
        excerpt: doc.excerpt || "",
        kind: doc.kind,
        audience: doc.audience,
        category: doc.category || "",
        subcategory: doc.subcategory || "",
        sort_order: doc.sort_order,
        body_markdown: doc.body_markdown
    };
}

function parseNumberInput(value: string): number {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) ? parsed : 0;
}

export default function AdminPage() {
    const [bootstrap, setBootstrap] = useState<BootstrapData | null>(null);
    const [documents, setDocuments] = useState<SiteDocument[]>([]);
    const [videos, setVideos] = useState<SiteVideo[]>([]);
    const [settings, setSettings] = useState<Record<string, string>>({});
    const [editor, setEditor] = useState<EditorState>(emptyEditor);
    const [activeSection, setActiveSection] = useState<AdminSection>("content");
    const [uploadTitle, setUploadTitle] = useState("");
    const [uploadDescription, setUploadDescription] = useState("");
    const [uploadAudience, setUploadAudience] = useState<"public" | "addon">("public");
    const [uploadSortOrder, setUploadSortOrder] = useState("0");
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [status, setStatus] = useState("");
    const [error, setError] = useState("");

    const load = useCallback(async () => {
        const [bootstrapData, docsData, settingsData, videosData] = await Promise.all([
            getBootstrap(),
            apiGet<DocumentsResponse>("/api/admin/documents"),
            apiGet<SettingsResponse>("/api/admin/settings"),
            apiGet<VideosResponse>("/api/admin/media")
        ]);

        setBootstrap(bootstrapData);
        setDocuments(docsData.documents);
        setVideos(videosData.videos);
        setSettings(settingsData.settings);
        setEditor((current) => {
            if (current.id) {
                const nextDoc = docsData.documents.find((doc) => doc.id === current.id);
                return nextDoc ? fromDocument(nextDoc) : (docsData.documents[0] ? fromDocument(docsData.documents[0]) : emptyEditor);
            }

            return docsData.documents[0] ? fromDocument(docsData.documents[0]) : current;
        });
    }, []);

    useEffect(() => {
        let cancelled = false;

        queueMicrotask(() => {
            if (cancelled) {
                return;
            }

            load().catch((loadError) => {
                if (!cancelled) {
                    setError(loadError instanceof Error ? loadError.message : "Failed to load admin");
                }
            });
        });

        return () => {
            cancelled = true;
        };
    }, [load]);

    async function handleSaveDocument() {
        try {
            setError("");
            setStatus("");
            await apiPost("/api/admin/documents", {
                id: editor.id,
                slug: editor.slug,
                title: editor.title,
                excerpt: editor.excerpt,
                kind: editor.kind,
                audience: editor.audience,
                category: editor.kind === "wiki" ? editor.category : "",
                subcategory: editor.kind === "wiki" ? editor.subcategory : "",
                sortOrder: editor.sort_order,
                bodyMarkdown: editor.body_markdown
            });
            await load();
            setStatus("Document saved.");
        } catch (saveError) {
            setError(saveError instanceof Error ? saveError.message : "Save failed");
        }
    }

    async function handleDeleteDocument() {
        if (!editor.id || !window.confirm("Delete this page?")) return;

        try {
            setError("");
            setStatus("");
            await apiDelete("/api/admin/documents", { id: editor.id });
            await load();
            setEditor(emptyEditor);
            setStatus("Document deleted.");
        } catch (deleteError) {
            setError(deleteError instanceof Error ? deleteError.message : "Delete failed");
        }
    }

    async function handleSaveSettings() {
        try {
            setError("");
            setStatus("");
            await apiPost("/api/admin/settings", { settings });
            await load();
            setStatus("Links saved.");
        } catch (saveError) {
            setError(saveError instanceof Error ? saveError.message : "Save failed");
        }
    }

    async function handleUploadVideo() {
        if (!uploadFile) {
            setError("Choose a video file first.");
            return;
        }

        try {
            setError("");
            setStatus("");
            const formData = new FormData();
            formData.append("title", uploadTitle);
            formData.append("description", uploadDescription);
            formData.append("audience", uploadAudience);
            formData.append("sortOrder", uploadSortOrder);
            formData.append("file", uploadFile);

            await apiForm("/api/admin/media", formData);
            setUploadTitle("");
            setUploadDescription("");
            setUploadAudience("public");
            setUploadSortOrder("0");
            setUploadFile(null);
            await load();
            setStatus("Video uploaded.");
        } catch (uploadError) {
            setError(uploadError instanceof Error ? uploadError.message : "Upload failed");
        }
    }

    async function handleDeleteVideo(id: number) {
        if (!window.confirm("Delete this video?")) return;

        try {
            setError("");
            setStatus("");
            await apiDelete("/api/admin/media", { id });
            await load();
            setStatus("Video deleted.");
        } catch (deleteError) {
            setError(deleteError instanceof Error ? deleteError.message : "Delete failed");
        }
    }

    function renderDocumentButton(document: SiteDocument) {
        const meta = document.kind === "wiki"
            ? getDocumentTaxonomyLabel(document)
            : `${document.kind} / ${document.audience}`;

        return (
            <button
                key={document.id}
                className={`sidebar-item ${editor.id === document.id ? "active" : ""}`}
                onClick={() => setEditor(fromDocument(document))}
            >
                <strong>{document.title}</strong>
                <span>{meta}</span>
            </button>
        );
    }

    function renderGroupedDocuments(items: SiteDocument[]) {
        const groups = buildDocumentGroups(items);

        if (!groups.length) {
            return <div className="muted-text sidebar-empty">No wiki pages yet.</div>;
        }

        return groups.map((group) => (
            <div key={group.key} className="sidebar-group">
                <div className="sidebar-group-label">{group.label}</div>
                {group.documents.map(renderDocumentButton)}
                {group.subgroups.map((subgroup) => (
                    <div key={subgroup.key} className="sidebar-subgroup">
                        <div className="sidebar-subgroup-label">{subgroup.label}</div>
                        {subgroup.documents.map(renderDocumentButton)}
                    </div>
                ))}
            </div>
        ));
    }

    if (!bootstrap) {
        return <div className="standalone-wrap"><div className="glass card">Loading...</div></div>;
    }

    const landingPages = documents.filter((doc) => doc.kind === "page");
    const publicWikiDocuments = documents.filter((doc) => doc.kind === "wiki" && doc.audience === "public");
    const addonWikiDocuments = documents.filter((doc) => doc.kind === "wiki" && doc.audience === "addon");
    const adminSections: Array<{ id: AdminSection; label: string; detail: string; badge: string }> = [
        {
            id: "content",
            label: "Content editor",
            detail: "Landing pages and both wiki trees.",
            badge: `${documents.length} docs`
        },
        {
            id: "links",
            label: "Site links",
            detail: "Public nav links and source URLs.",
            badge: "4 links"
        },
        {
            id: "media",
            label: "Media library",
            detail: "Upload and manage feature videos.",
            badge: `${videos.length} videos`
        }
    ];

    let sectionBody;

    if (activeSection === "links") {
        sectionBody = (
            <motion.section
                key="links"
                className="glass panel admin-section-panel"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
            >
                <div className="section-heading">
                    <div>
                        <h2>Site links</h2>
                        <p className="muted-text admin-section-copy">Keep the public navigation links in one place instead of mixing them into the content editor.</p>
                    </div>
                    <span className="badge neutral">Public buttons</span>
                </div>

                <div className="admin-links-grid">
                    <div className="field-stack">
                        <label className="label">Discord URL</label>
                        <input className="input" value={settings.discord_url || ""} onChange={(e) => setSettings((prev) => ({ ...prev, discord_url: e.target.value }))} />
                    </div>
                    <div className="field-stack">
                        <label className="label">Support URL</label>
                        <input className="input" value={settings.support_url || ""} onChange={(e) => setSettings((prev) => ({ ...prev, support_url: e.target.value }))} />
                    </div>
                    <div className="field-stack">
                        <label className="label">Download URL</label>
                        <input className="input" value={settings.download_url || ""} onChange={(e) => setSettings((prev) => ({ ...prev, download_url: e.target.value }))} />
                    </div>
                    <div className="field-stack">
                        <label className="label">Public source URL</label>
                        <input className="input" value={settings.source_code_url || ""} onChange={(e) => setSettings((prev) => ({ ...prev, source_code_url: e.target.value }))} />
                    </div>
                </div>

                <div className="action-row">
                    <button className="button accent" onClick={handleSaveSettings}>Save links</button>
                </div>
            </motion.section>
        );
    } else if (activeSection === "media") {
        sectionBody = (
            <motion.section
                key="media"
                className="glass panel admin-section-panel"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
            >
                <div className="section-heading">
                    <div>
                        <h2>Media library</h2>
                        <p className="muted-text admin-section-copy">Upload feature videos and manage the published media list from one view.</p>
                    </div>
                    <span className="badge neutral">{videos.length} total</span>
                </div>

                <div className="admin-media-layout">
                    <div className="admin-card">
                        <div className="section-heading">
                            <h2>Upload feature video</h2>
                            <span className="badge neutral">R2-backed</span>
                        </div>
                        <div className="field-stack">
                            <label className="label">Title</label>
                            <input className="input" value={uploadTitle} onChange={(e) => setUploadTitle(e.target.value)} />
                        </div>
                        <div className="field-stack">
                            <label className="label">Description</label>
                            <input className="input" value={uploadDescription} onChange={(e) => setUploadDescription(e.target.value)} />
                        </div>
                        <div className="field-grid">
                            <div className="field-stack">
                                <label className="label">Audience</label>
                                <select className="select" value={uploadAudience} onChange={(e) => setUploadAudience(e.target.value as "public" | "addon")}>
                                    <option value="public">public</option>
                                    <option value="addon">addon</option>
                                </select>
                            </div>
                            <div className="field-stack">
                                <label className="label">Sort order</label>
                                <input className="input" value={uploadSortOrder} onChange={(e) => setUploadSortOrder(e.target.value)} />
                            </div>
                        </div>
                        <div className="field-stack">
                            <label className="label">Video file</label>
                            <input className="input" type="file" accept="video/*" onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)} />
                        </div>
                        <p className="muted-text">Browsers can still save streamed media, even when the normal download control is hidden.</p>
                        <button className="button accent full-width" onClick={handleUploadVideo}>Upload video</button>
                    </div>

                    <div className="admin-card">
                        <div className="section-heading">
                            <h2>Published videos</h2>
                            <span className="badge neutral">{videos.length} total</span>
                        </div>
                        <div className="video-stack">
                            {videos.map((video) => (
                                <div key={video.id} className="video-row">
                                    <div>
                                        <strong>{video.title}</strong>
                                        <div className="muted-text">{video.audience} / {Math.round(video.size_bytes / 1024 / 1024)} MB</div>
                                    </div>
                                    <div className="action-row compact">
                                        <a className="button" href={video.streamUrl} target="_blank" rel="noreferrer">Preview</a>
                                        <button className="button danger" onClick={() => handleDeleteVideo(video.id)}>Delete</button>
                                    </div>
                                </div>
                            ))}
                            {videos.length === 0 ? <div className="muted-text">No videos uploaded yet.</div> : null}
                        </div>
                    </div>
                </div>
            </motion.section>
        );
    } else {
        sectionBody = (
            <motion.section
                key="content"
                className="glass panel admin-section-panel"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
            >
                <div className="section-heading">
                    <div>
                        <h2>Content editor</h2>
                        <p className="muted-text admin-section-copy">Use the document rail to pick a page, then edit it in the wider workspace.</p>
                    </div>
                    <button className="button" onClick={() => setEditor(emptyEditor)}>New page</button>
                </div>

                <div className="admin-content-layout">
                    <aside className="admin-sidebar admin-document-sidebar">
                        <div className="admin-sidebar-section">
                            <div className="admin-section-label">Landing pages</div>
                            {landingPages.length > 0 ? landingPages.map(renderDocumentButton) : <div className="muted-text sidebar-empty">No landing pages yet.</div>}
                        </div>

                        <div className="admin-sidebar-section">
                            <div className="admin-section-label">Public wiki</div>
                            {renderGroupedDocuments(publicWikiDocuments)}
                        </div>

                        <div className="admin-sidebar-section">
                            <div className="admin-section-label">Addon wiki</div>
                            {renderGroupedDocuments(addonWikiDocuments)}
                        </div>
                    </aside>

                    <div className="editor-surface">
                        <div className="admin-editor-fields">
                            <div className="field-stack">
                                <label className="label">Title</label>
                                <input className="input" value={editor.title} onChange={(e) => setEditor((prev) => ({ ...prev, title: e.target.value }))} />
                            </div>
                            <div className="field-stack">
                                <label className="label">Slug</label>
                                <input className="input" value={editor.slug} onChange={(e) => setEditor((prev) => ({ ...prev, slug: e.target.value }))} />
                            </div>
                            <div className="field-stack">
                                <label className="label">Kind</label>
                                <select className="select" value={editor.kind} onChange={(e) => setEditor((prev) => ({ ...prev, kind: e.target.value as "page" | "wiki" }))}>
                                    <option value="wiki">wiki</option>
                                    <option value="page">page</option>
                                </select>
                            </div>
                            <div className="field-stack">
                                <label className="label">Audience</label>
                                <select className="select" value={editor.audience} onChange={(e) => setEditor((prev) => ({ ...prev, audience: e.target.value as "public" | "addon" }))}>
                                    <option value="public">public</option>
                                    <option value="addon">addon</option>
                                </select>
                            </div>
                            <div className="field-stack">
                                <label className="label">Category</label>
                                <input
                                    className="input"
                                    value={editor.category}
                                    disabled={editor.kind !== "wiki"}
                                    placeholder={editor.kind === "wiki" ? "General" : "Wiki only"}
                                    onChange={(e) => setEditor((prev) => ({ ...prev, category: e.target.value }))}
                                />
                            </div>
                            <div className="field-stack">
                                <label className="label">Subcategory</label>
                                <input
                                    className="input"
                                    value={editor.subcategory}
                                    disabled={editor.kind !== "wiki"}
                                    placeholder={editor.kind === "wiki" ? "Optional" : "Wiki only"}
                                    onChange={(e) => setEditor((prev) => ({ ...prev, subcategory: e.target.value }))}
                                />
                            </div>
                            <div className="field-stack">
                                <label className="label">Sort order</label>
                                <input
                                    className="input"
                                    type="number"
                                    value={editor.sort_order}
                                    onChange={(e) => setEditor((prev) => ({ ...prev, sort_order: parseNumberInput(e.target.value) }))}
                                />
                            </div>
                            <div className="field-stack col-span-4">
                                <label className="label">Excerpt</label>
                                <input className="input" value={editor.excerpt} onChange={(e) => setEditor((prev) => ({ ...prev, excerpt: e.target.value }))} />
                            </div>
                        </div>

                        <div className="editor-grid admin-editor-grid">
                            <div className="field-stack">
                                <label className="label">Markdown</label>
                                <textarea
                                    className="textarea admin-markdown"
                                    value={editor.body_markdown}
                                    onChange={(e) => setEditor((prev) => ({ ...prev, body_markdown: e.target.value }))}
                                />
                            </div>
                            <div className="field-stack">
                                <label className="label">Live preview</label>
                                <div className="preview-panel admin-preview-panel">
                                    <MarkdownPreview markdown={editor.body_markdown} />
                                </div>
                            </div>
                        </div>

                        <div className="action-row">
                            <button className="button accent" onClick={handleSaveDocument}>Save page</button>
                            {editor.id ? <button className="button danger" onClick={handleDeleteDocument}>Delete page</button> : null}
                        </div>
                    </div>
                </div>
            </motion.section>
        );
    }

    return (
        <SiteFrame session={bootstrap.session} settings={bootstrap.settings} wide>
            <section className="admin-shell">
                <aside className="glass panel admin-nav">
                    <div className="admin-nav-header">
                        <span className="eyebrow">Admin</span>
                        <h2>Controls</h2>
                        <p className="muted-text">Switch between admin categories from the left rail, then work in the full-width panel.</p>
                    </div>

                    <div className="admin-nav-list">
                        {adminSections.map((section) => (
                            <button
                                key={section.id}
                                className={`admin-nav-button ${activeSection === section.id ? "active" : ""}`}
                                onClick={() => setActiveSection(section.id)}
                            >
                                <div>
                                    <strong>{section.label}</strong>
                                    <span>{section.detail}</span>
                                </div>
                                <span className="badge neutral">{section.badge}</span>
                            </button>
                        ))}
                    </div>

                    {status ? <div className="success-text">{status}</div> : null}
                    {error ? <div className="error-text">{error}</div> : null}
                </aside>

                <div className="admin-main">
                    {sectionBody}
                </div>
            </section>
        </SiteFrame>
    );
}
