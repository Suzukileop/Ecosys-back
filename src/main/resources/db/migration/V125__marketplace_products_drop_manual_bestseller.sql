-- Bestsellers are now computed from units sold (per shop and per catalogue), not flagged by hand.
ALTER TABLE marketplace_products
    DROP COLUMN IF EXISTS is_bestseller;
