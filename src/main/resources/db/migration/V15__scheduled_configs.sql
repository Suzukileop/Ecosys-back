CREATE TABLE scheduled_configs (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  niche_request_id     UUID NOT NULL UNIQUE REFERENCES niche_requests(id) ON DELETE CASCADE,
  client_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

  publication_days     JSONB NOT NULL DEFAULT '[1,3,5]'::jsonb,

  publication_time     VARCHAR(5) NOT NULL DEFAULT '09:00',

  platforms            JSONB NOT NULL DEFAULT '[]'::jsonb,

  is_active            BOOLEAN DEFAULT true,
  created_at           TIMESTAMP NOT NULL DEFAULT now(),
  updated_at           TIMESTAMP
);

CREATE INDEX idx_sched_configs_client ON scheduled_configs(client_id);
CREATE INDEX idx_sched_configs_active ON scheduled_configs(is_active)
  WHERE is_active = true;
