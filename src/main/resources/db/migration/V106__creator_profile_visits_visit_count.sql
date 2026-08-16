-- Per-visitor visit tally (unique visitors stay one row; this counts how many times they returned).
ALTER TABLE creator_profile_visits
    ADD COLUMN IF NOT EXISTS visit_count INTEGER NOT NULL DEFAULT 1;
