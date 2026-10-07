-- Supports RefreshTokenPurgeJob, which deletes expired and long-revoked tokens every hour.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry_date ON refresh_tokens (expiry_date);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at ON refresh_tokens (revoked_at) WHERE is_revoked = true;
