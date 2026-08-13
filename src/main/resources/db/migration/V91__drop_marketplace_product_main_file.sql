-- Remove private product delivery file storage; listings no longer require a main file.
ALTER TABLE marketplace_products
    DROP COLUMN IF EXISTS main_file_r2_key;
