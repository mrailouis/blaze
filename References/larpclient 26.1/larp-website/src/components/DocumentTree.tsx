import { Link } from "react-router-dom";
import { buildDocumentGroups } from "../lib/documentGroups";
import type { SiteDocument } from "../types";

export default function DocumentTree(
    {
        documents,
        basePath,
        selectedSlug,
        emptyLabel = "No documents yet."
    }: {
        documents: SiteDocument[];
        basePath: string;
        selectedSlug?: string;
        emptyLabel?: string;
    }
) {
    const groups = buildDocumentGroups(documents);

    if (!groups.length) {
        return <div className="empty-state">{emptyLabel}</div>;
    }

    return (
        <div className="doc-tree">
            {groups.map((group) => (
                <div key={group.key} className="doc-tree-group">
                    <div className="doc-tree-group-label">{group.label}</div>
                    {group.documents.map((document) => (
                        <Link
                            key={document.id}
                            className={`doc-tree-link ${selectedSlug === document.slug ? "active" : ""}`}
                            to={`${basePath}/${document.slug}`}
                        >
                            <span className="doc-tree-prefix">-</span>
                            <span>{document.title}</span>
                        </Link>
                    ))}
                    {group.subgroups.map((subgroup) => (
                        <div key={subgroup.key} className="doc-tree-subgroup">
                            <div className="doc-tree-subgroup-label">
                                <span className="doc-tree-prefix">-</span>
                                <span>{subgroup.label}</span>
                            </div>
                            {subgroup.documents.map((document) => (
                                <Link
                                    key={document.id}
                                    className={`doc-tree-link nested ${selectedSlug === document.slug ? "active" : ""}`}
                                    to={`${basePath}/${document.slug}`}
                                >
                                    <span className="doc-tree-prefix">--</span>
                                    <span>{document.title}</span>
                                </Link>
                            ))}
                        </div>
                    ))}
                </div>
            ))}
        </div>
    );
}
