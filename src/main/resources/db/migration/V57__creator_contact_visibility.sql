ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS contact_visibility JSONB NOT NULL DEFAULT '{
        "website": "PUBLIC",
        "email": "MEMBERS",
        "phone": "MEMBERS",
        "availability": "PUBLIC",
        "address": "HIDDEN",
        "social": "PUBLIC"
    }'::jsonb;
