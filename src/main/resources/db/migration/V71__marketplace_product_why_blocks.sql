ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS why_product_blocks JSONB NOT NULL DEFAULT '[]'::jsonb;
