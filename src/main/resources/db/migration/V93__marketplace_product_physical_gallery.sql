-- Physical products: companion gallery images (URLs)
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS gallery_image_urls jsonb NOT NULL DEFAULT '[]'::jsonb;
