ALTER TABLE site_documents ADD COLUMN category TEXT;
ALTER TABLE site_documents ADD COLUMN subcategory TEXT;

CREATE INDEX IF NOT EXISTS idx_site_documents_kind_audience_category_sort
    ON site_documents (kind, audience, category, subcategory, sort_order, updated_at);

INSERT OR IGNORE INTO site_settings (key, value)
VALUES ('source_code_url', 'https://github.com/replace-me/larpclient-public');
