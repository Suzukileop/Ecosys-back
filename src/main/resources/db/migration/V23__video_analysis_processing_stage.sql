ALTER TABLE video_analyses
  ADD COLUMN IF NOT EXISTS processing_stage VARCHAR(32);
