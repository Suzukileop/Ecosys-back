-- Merchandising status a creator attaches to a product (Promotion, Clearance, Trending, …), shown as
-- badges on the product cards. A short list of ProductStatusLabel names; empty for existing rows.
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS status_labels jsonb NOT NULL DEFAULT '[]'::jsonb;
