-- Sprint 4 : pipeline analyse vidéo IA (Grok + Nano Banana + MiniMax)
--
-- Choix architecture : pas de table platform_models.
-- Les vidéos modèles agent sont stockées sur niche_requests.model_video_url
-- (demo_content_url reste dédié à la démo de validation agent).
-- Les analyses client sont dans video_analyses / video_segments.

ALTER TABLE niche_requests
  ADD COLUMN IF NOT EXISTS model_video_url VARCHAR(500);

CREATE TABLE user_credits (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  balance     INT NOT NULL DEFAULT 0 CHECK (balance >= 0),
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP
);

CREATE INDEX idx_user_credits_user_id ON user_credits(user_id);

CREATE TABLE credit_transactions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount      INT NOT NULL,
  reason      VARCHAR(200) NOT NULL,
  ref_id      UUID,
  created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_transactions_user_id ON credit_transactions(user_id);
CREATE INDEX idx_credit_transactions_ref_id ON credit_transactions(ref_id);

CREATE TABLE video_analyses (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  video_url       VARCHAR(500) NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  transcription   TEXT,
  segments_count  INT,
  processing_ms   BIGINT,
  credits_used    INT DEFAULT 0,
  error_message   TEXT,
  deleted_at      TIMESTAMP,
  created_at      TIMESTAMP NOT NULL DEFAULT now(),
  updated_at      TIMESTAMP
);

CREATE INDEX idx_video_analyses_user_id ON video_analyses(user_id);
CREATE INDEX idx_video_analyses_status ON video_analyses(status);
CREATE INDEX idx_video_analyses_active ON video_analyses(user_id)
  WHERE deleted_at IS NULL;

CREATE TABLE video_segments (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_id           UUID NOT NULL REFERENCES video_analyses(id) ON DELETE CASCADE,
  seq_number            INT NOT NULL,
  start_time_ms         BIGINT,
  end_time_ms           BIGINT,
  transcript            TEXT,
  background_desc       TEXT,
  image_prompt          TEXT,
  text_overlay          TEXT,
  audio_desc            TEXT,
  effects_desc          TEXT,
  capcut_effects        JSONB DEFAULT '[]'::jsonb,
  resources             JSONB DEFAULT '[]'::jsonb,
  intention             TEXT,
  transition_to_next    TEXT,
  generated_images      JSONB DEFAULT '[]'::jsonb,
  generated_video_url   VARCHAR(500),
  created_at            TIMESTAMP NOT NULL DEFAULT now(),
  updated_at            TIMESTAMP
);

CREATE INDEX idx_video_segments_analysis_id ON video_segments(analysis_id);
CREATE UNIQUE INDEX idx_video_segments_analysis_seq ON video_segments(analysis_id, seq_number);
