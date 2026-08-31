ALTER TABLE creator_profiles
    ADD COLUMN profile_stack JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Migrate legacy specialty_tags (plain strings) into profile_stack as { "name": tag } objects.
UPDATE creator_profiles cp
SET profile_stack = COALESCE((
    SELECT jsonb_agg(jsonb_build_object('name', tag))
    FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) AS tag
    WHERE tag IS NOT NULL AND btrim(tag) <> ''
), '[]'::jsonb)
WHERE cp.profile_stack = '[]'::jsonb
  AND jsonb_array_length(COALESCE(cp.specialty_tags, '[]'::jsonb)) > 0;
