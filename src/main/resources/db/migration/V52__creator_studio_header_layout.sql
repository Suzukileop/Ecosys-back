ALTER TABLE creator_profiles
    ADD COLUMN studio_header_layout VARCHAR(20) NOT NULL DEFAULT 'BANNER';

ALTER TABLE creator_profiles
    ADD CONSTRAINT chk_creator_studio_header_layout
        CHECK (studio_header_layout IN ('BANNER', 'SPLIT'));
