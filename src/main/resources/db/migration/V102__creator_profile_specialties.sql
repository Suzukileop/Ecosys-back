-- Multi-select specialties (closed taxonomy, max 3) + free keyword tags.
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS specialties jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS specialty_tags jsonb NOT NULL DEFAULT '[]'::jsonb;

-- Backfill from legacy single specialite, mapping common aliases to chip labels.
UPDATE creator_profiles
SET specialties = jsonb_build_array(
    CASE
        WHEN LOWER(specialite) ~ '(data[[:space:]]*scien)' THEN 'Data science'
        WHEN LOWER(specialite) ~ '(ui[[:space:]/]*ux|user[[:space:]]*experience)' THEN 'UI / UX'
        WHEN LOWER(specialite) ~ '(video)' THEN 'Video editor'
        WHEN LOWER(specialite) ~ '(develop|program|software)' THEN 'Developer'
        WHEN LOWER(specialite) ~ '(illustrat)' THEN 'Illustration'
        WHEN LOWER(specialite) ~ '(photograph|photo)' THEN 'Photography'
        WHEN LOWER(specialite) ~ '(brand)' THEN 'Branding'
        WHEN LOWER(specialite) ~ '(market)' THEN 'Marketing'
        WHEN LOWER(specialite) ~ '(music)' THEN 'Music'
        WHEN LOWER(specialite) ~ '(writ|copy)' THEN 'Writing'
        WHEN LOWER(specialite) ~ '(^|[^a-z])3d([^a-z]|$)' THEN '3D'
        WHEN LOWER(specialite) ~ '(design)' THEN 'Design'
        ELSE NULL
    END
)
WHERE (specialties IS NULL OR specialties = '[]'::jsonb)
  AND specialite IS NOT NULL
  AND TRIM(specialite) <> '';

UPDATE creator_profiles
SET specialties = '[]'::jsonb
WHERE specialties = jsonb_build_array(NULL);

UPDATE creator_profiles
SET specialite = specialties ->> 0
WHERE jsonb_array_length(specialties) > 0
  AND (specialite IS NULL OR TRIM(specialite) = '' OR specialite <> (specialties ->> 0));

CREATE INDEX IF NOT EXISTS idx_creator_profiles_specialties
    ON creator_profiles USING GIN (specialties);

CREATE INDEX IF NOT EXISTS idx_creator_profiles_specialty_tags
    ON creator_profiles USING GIN (specialty_tags);
