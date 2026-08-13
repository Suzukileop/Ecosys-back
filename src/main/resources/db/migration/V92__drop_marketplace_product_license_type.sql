-- Remove product license type; licensing is no longer part of marketplace listings.
ALTER TABLE marketplace_products
    DROP COLUMN IF EXISTS license_type;
