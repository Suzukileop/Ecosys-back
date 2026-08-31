-- Reference table for spoken-language proficiency levels (beginner → expert).
CREATE TABLE language_proficiency_levels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    label VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL
);

INSERT INTO language_proficiency_levels (code, label, sort_order) VALUES
    ('beginner', 'Beginner', 1),
    ('intermediate', 'Intermediate', 2),
    ('advanced', 'Advanced', 3),
    ('expert', 'Expert', 4);

-- Migrate spoken_languages JSONB from plain strings to { "name", "level" } objects.
UPDATE creator_profiles
SET spoken_languages = COALESCE(
    (
        SELECT jsonb_agg(
            CASE
                WHEN jsonb_typeof(elem) = 'string' THEN jsonb_build_object('name', elem)
                WHEN jsonb_typeof(elem) = 'object' THEN
                    jsonb_build_object(
                        'name', COALESCE(elem ->> 'name', elem ->> 'value'),
                        'level', NULLIF(elem ->> 'level', '')
                    )
                ELSE elem
            END
        )
        FROM jsonb_array_elements(spoken_languages) AS elem
    ),
    '[]'::jsonb
)
WHERE spoken_languages IS NOT NULL
  AND jsonb_typeof(spoken_languages) = 'array'
  AND jsonb_array_length(spoken_languages) > 0;
