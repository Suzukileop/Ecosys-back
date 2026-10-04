-- Units on hand for physical products; NULL for digital products (unlimited by nature).
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS stock_quantity INTEGER;

ALTER TABLE marketplace_products
    ADD CONSTRAINT chk_marketplace_products_stock_quantity_non_negative
    CHECK (stock_quantity IS NULL OR stock_quantity >= 0);
