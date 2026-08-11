ALTER TABLE users
  ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
  ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE users
  ADD COLUMN provider_user_id VARCHAR(255);

CREATE UNIQUE INDEX idx_users_oauth_provider_user
  ON users (auth_provider, provider_user_id)
  WHERE provider_user_id IS NOT NULL;
