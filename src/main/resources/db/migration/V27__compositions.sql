CREATE TABLE compositions (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_id       UUID NOT NULL REFERENCES video_analyses(id),
  user_id           UUID NOT NULL REFERENCES users(id),
  title             VARCHAR(300) NOT NULL DEFAULT 'Ma composition',
  composition_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
  format            VARCHAR(10) NOT NULL DEFAULT '9:16',
  duration_seconds  FLOAT NOT NULL DEFAULT 0,
  export_status     VARCHAR(20) DEFAULT 'idle',
  export_url        VARCHAR(500),
  export_job_id     VARCHAR(100),
  credits_used      INT DEFAULT 0,
  created_at        TIMESTAMP NOT NULL DEFAULT now(),
  updated_at        TIMESTAMP NOT NULL DEFAULT now(),
  deleted_at        TIMESTAMP
);
CREATE INDEX idx_compositions_user     ON compositions(user_id);
CREATE INDEX idx_compositions_analysis ON compositions(analysis_id);

CREATE TABLE composition_assets (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  composition_id UUID REFERENCES compositions(id),
  user_id        UUID NOT NULL REFERENCES users(id),
  r2_url         VARCHAR(500) NOT NULL,
  file_type      VARCHAR(20) NOT NULL,
  file_size      BIGINT,
  original_name  VARCHAR(300),
  created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_composition_assets_composition ON composition_assets(composition_id);
