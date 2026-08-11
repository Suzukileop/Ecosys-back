-- Keep V1 immutable for existing environments and apply incremental changes here.

ALTER TABLE refresh_tokens
    ALTER COLUMN token TYPE TEXT;

CREATE INDEX IF NOT EXISTS idx_users_email_active
    ON users(email) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry_active
    ON refresh_tokens(expiry_date) WHERE is_revoked = false;

CREATE INDEX IF NOT EXISTS idx_audit_logs_action
    ON audit_logs(action);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs(created_at);
