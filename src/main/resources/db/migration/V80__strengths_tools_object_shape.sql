-- Migrate legacy strengths_tools_mastered string[] → [{ "name", "description" }, ...]
UPDATE creator_profiles
SET strengths_tools_mastered = COALESCE((
    SELECT jsonb_agg(normalized.item)
    FROM (
        SELECT
            CASE
                WHEN jsonb_typeof(elem) = 'string' THEN jsonb_build_object(
                    'name', elem,
                    'description', NULL
                )
                WHEN jsonb_typeof(elem) = 'object'
                     AND (elem ? 'name' OR elem ? 'value') THEN jsonb_build_object(
                    'name', COALESCE(elem ->> 'name', elem ->> 'value'),
                    'description', NULLIF(elem ->> 'description', '')
                )
                ELSE NULL
            END AS item
        FROM jsonb_array_elements(
            CASE
                WHEN jsonb_typeof(strengths_tools_mastered) = 'array' THEN strengths_tools_mastered
                ELSE '[]'::jsonb
            END
        ) AS elem
    ) AS normalized
    WHERE normalized.item IS NOT NULL
), '[]'::jsonb)
WHERE TRUE;
