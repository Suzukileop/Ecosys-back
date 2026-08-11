ALTER TABLE creator_profiles
    DROP CONSTRAINT IF EXISTS chk_creator_studio_header_layout;

ALTER TABLE creator_profiles
    ADD CONSTRAINT chk_creator_studio_header_layout
        CHECK (studio_header_layout IN ('BANNER', 'SPLIT', 'VIP_GOLD', 'VIP_AURORA'));
