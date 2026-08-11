ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS demo_subtitles JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Migrate legacy single-line demo descriptions into subtitles array.
UPDATE marketplace_products
SET demo_subtitles = jsonb_build_array(demo_description)
WHERE demo_description IS NOT NULL
  AND btrim(demo_description) <> ''
  AND (demo_subtitles IS NULL OR demo_subtitles = '[]'::jsonb);
