ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS why_me_blocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS experience_blocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS years_of_experience INT,
    ADD COLUMN IF NOT EXISTS strengths_tools_mastered JSONB NOT NULL DEFAULT '[]'::jsonb;
