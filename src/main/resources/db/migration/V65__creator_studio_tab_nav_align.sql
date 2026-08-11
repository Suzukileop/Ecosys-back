ALTER TABLE creator_profiles
    ADD COLUMN studio_tab_nav_align VARCHAR(10) NOT NULL DEFAULT 'LEFT';

ALTER TABLE creator_profiles
    ADD CONSTRAINT chk_creator_studio_tab_nav_align
        CHECK (studio_tab_nav_align IN ('LEFT', 'CENTER', 'RIGHT'));
