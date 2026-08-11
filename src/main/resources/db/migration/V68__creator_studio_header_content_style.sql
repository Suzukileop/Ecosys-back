ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS studio_header_content_style VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE creator_profiles
    DROP CONSTRAINT IF EXISTS chk_creator_studio_header_content_style;

ALTER TABLE creator_profiles
    ADD CONSTRAINT chk_creator_studio_header_content_style
        CHECK (studio_header_content_style IN ('DEFAULT', 'COMPACT', 'CENTERED', 'GRID'));
