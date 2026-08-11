ALTER TABLE creator_profiles
  ADD COLUMN IF NOT EXISTS typical_response_time VARCHAR(40);
