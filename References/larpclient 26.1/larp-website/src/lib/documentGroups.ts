import type { SiteDocument } from "../types";

const CATEGORY_ORDER = ["Skyblock", "Dungeons", "Kuudra", "Misc", "General"] as const;
const SUBCATEGORY_ORDER: Record<string, readonly string[]> = {
    Dungeons: ["Floor 7"]
};

export type DocumentSubgroup = {
    key: string;
    label: string;
    documents: SiteDocument[];
};

export type DocumentGroup = {
    key: string;
    label: string;
    documents: SiteDocument[];
    subgroups: DocumentSubgroup[];
};

type GroupState = {
    key: string;
    label: string;
    documents: SiteDocument[];
    subgroups: DocumentSubgroup[];
    subgroupMap: Map<string, DocumentSubgroup>;
};

function normalizeTaxonomyValue(value: string | null | undefined): string | null {
    const normalized = value?.trim();
    return normalized ? normalized : null;
}

function compareByKnownOrder(left: string, right: string, order: readonly string[]): number {
    const leftIndex = order.findIndex((value) => value.toLowerCase() === left.toLowerCase());
    const rightIndex = order.findIndex((value) => value.toLowerCase() === right.toLowerCase());

    if (leftIndex !== -1 || rightIndex !== -1) {
        if (leftIndex === -1) return 1;
        if (rightIndex === -1) return -1;
        if (leftIndex !== rightIndex) return leftIndex - rightIndex;
    }

    return left.localeCompare(right);
}

function compareDocuments(left: SiteDocument, right: SiteDocument): number {
    if (left.sort_order !== right.sort_order) {
        return left.sort_order - right.sort_order;
    }

    return left.title.localeCompare(right.title);
}

export function getDocumentCategoryLabel(document: Pick<SiteDocument, "category">): string {
    return normalizeTaxonomyValue(document.category) ?? "General";
}

export function getDocumentSubcategoryLabel(document: Pick<SiteDocument, "subcategory">): string | null {
    return normalizeTaxonomyValue(document.subcategory);
}

export function getDocumentTaxonomyLabel(document: Pick<SiteDocument, "category" | "subcategory">): string {
    const category = getDocumentCategoryLabel(document);
    const subcategory = getDocumentSubcategoryLabel(document);
    return subcategory ? `${category} / ${subcategory}` : category;
}

export function buildDocumentGroups(documents: SiteDocument[]): DocumentGroup[] {
    const groups = new Map<string, GroupState>();

    for (const document of documents) {
        const category = getDocumentCategoryLabel(document);
        const categoryKey = category.toLowerCase();
        const existingGroup = groups.get(categoryKey);
        const group = existingGroup ?? {
            key: categoryKey,
            label: category,
            documents: [],
            subgroups: [],
            subgroupMap: new Map<string, DocumentSubgroup>()
        };

        if (!existingGroup) {
            groups.set(categoryKey, group);
        }

        const subcategory = getDocumentSubcategoryLabel(document);
        if (!subcategory) {
            group.documents.push(document);
            continue;
        }

        const subcategoryKey = `${categoryKey}::${subcategory.toLowerCase()}`;
        const existingSubgroup = group.subgroupMap.get(subcategoryKey);
        const subgroup = existingSubgroup ?? {
            key: subcategoryKey,
            label: subcategory,
            documents: []
        };

        if (!existingSubgroup) {
            group.subgroupMap.set(subcategoryKey, subgroup);
            group.subgroups.push(subgroup);
        }

        subgroup.documents.push(document);
    }

    return Array.from(groups.values())
        .sort((left, right) => compareByKnownOrder(left.label, right.label, CATEGORY_ORDER))
        .map((group) => ({
            key: group.key,
            label: group.label,
            documents: [...group.documents].sort(compareDocuments),
            subgroups: [...group.subgroups]
                .sort((left, right) => compareByKnownOrder(left.label, right.label, SUBCATEGORY_ORDER[group.label] ?? []))
                .map((subgroup) => ({
                    ...subgroup,
                    documents: [...subgroup.documents].sort(compareDocuments)
                }))
        }));
}
