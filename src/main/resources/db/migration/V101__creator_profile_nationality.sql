-- Citizenship / nationality, independent from GPS residence (location_country).
-- ISO 3166-1 alpha-2 (e.g. FR, US, MA).
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS nationality VARCHAR(2);

CREATE INDEX IF NOT EXISTS idx_creator_profiles_nationality
    ON creator_profiles (nationality)
    WHERE nationality IS NOT NULL;
