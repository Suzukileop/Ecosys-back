ALTER TABLE creator_profiles
    ADD COLUMN about_skills jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN about_strengths jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN about_systems_tools jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN about_interests jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN about_education jsonb NOT NULL DEFAULT '[]'::jsonb;
