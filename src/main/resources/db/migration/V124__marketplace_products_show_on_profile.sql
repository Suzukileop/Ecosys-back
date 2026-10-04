-- Lets a creator keep a published product in the marketplace and shop while hiding it from the
-- Products tab of their public profile.
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS show_on_profile BOOLEAN NOT NULL DEFAULT TRUE;
