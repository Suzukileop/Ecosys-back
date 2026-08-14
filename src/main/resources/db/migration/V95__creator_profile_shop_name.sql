-- Public shop / boutique name shown on products and searchable in Explore.
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS shop_name VARCHAR(120);
