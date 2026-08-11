ALTER TABLE notifications
    ADD COLUMN ref_secondary_id UUID;

CREATE INDEX idx_notifications_ref_secondary_id ON notifications(ref_secondary_id);
