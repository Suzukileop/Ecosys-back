-- Add backward-compatible metadata keys to every creator strength/tool object.
UPDATE creator_profiles
SET strengths_tools_mastered = COALESCE((
    SELECT jsonb_agg(
        CASE
            WHEN jsonb_typeof(element.value) = 'object' THEN
                jsonb_build_object(
                    'category', NULL,
                    'level', NULL,
                    'useCases', '[]'::jsonb,
                    'experienceYears', NULL,
                    'experienceLabel', NULL,
                    'currentlyUsed', NULL
                ) || element.value
            WHEN jsonb_typeof(element.value) = 'string' THEN
                jsonb_build_object(
                    'name', element.value,
                    'description', NULL,
                    'category', NULL,
                    'level', NULL,
                    'useCases', '[]'::jsonb,
                    'experienceYears', NULL,
                    'experienceLabel', NULL,
                    'currentlyUsed', NULL
                )
            ELSE element.value
        END
        ORDER BY element.ordinality
    )
    FROM jsonb_array_elements(
        CASE
            WHEN jsonb_typeof(strengths_tools_mastered) = 'array'
                THEN strengths_tools_mastered
            ELSE '[]'::jsonb
        END
    ) WITH ORDINALITY AS element(value, ordinality)
), '[]'::jsonb);
