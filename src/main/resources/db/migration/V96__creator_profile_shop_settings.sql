-- Shop settings for My Product / public shop page.
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS shop_selling_focus VARCHAR(200),
    ADD COLUMN IF NOT EXISTS shop_description VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS shop_cover_url VARCHAR(1000);
