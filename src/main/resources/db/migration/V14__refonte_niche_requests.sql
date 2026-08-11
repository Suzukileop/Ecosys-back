-- Refonte niche_requests : flux unique sans service_request_id.
-- Dépendances vers niche_requests (ex. futures FK) sont supprimées par CASCADE.

DROP TABLE IF EXISTS niche_requests CASCADE;

CREATE TABLE niche_requests (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  client_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  agent_id                UUID REFERENCES users(id) ON DELETE SET NULL,

  niche_theme             VARCHAR(200) NOT NULL,
  description             TEXT NOT NULL,
  language                VARCHAR(10) NOT NULL DEFAULT 'FR',

  nb_posts_per_week       SMALLINT NOT NULL CHECK (nb_posts_per_week BETWEEN 1 AND 14),

  platforms               JSONB NOT NULL DEFAULT '[]'::jsonb,

  ref_type                VARCHAR(10),
  ref_mct_code            VARCHAR(20),
  ref_external_url        VARCHAR(500),
  ref_file_url            VARCHAR(500),

  monthly_amount_cents    INT,

  unique_code             VARCHAR(20) UNIQUE,

  status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',

  bot_confirmed           BOOLEAN DEFAULT false,
  bot_confirmed_at        TIMESTAMP,

  demo_content_url        VARCHAR(500),
  agent_notes             TEXT,
  proposed_at             TIMESTAMP,

  validated_at            TIMESTAMP,
  rejection_reason        TEXT,

  payment_status          VARCHAR(20) DEFAULT 'UNPAID',
  stripe_session_id       VARCHAR(300),
  stripe_subscription_id  VARCHAR(300),
  paid_at                 TIMESTAMP,

  activated_at            TIMESTAMP,

  deadline                TIMESTAMP,
  deleted_at              TIMESTAMP,
  created_at              TIMESTAMP NOT NULL DEFAULT now(),
  updated_at              TIMESTAMP
);

CREATE INDEX idx_niche_requests_client   ON niche_requests(client_id);
CREATE INDEX idx_niche_requests_agent    ON niche_requests(agent_id);
CREATE INDEX idx_niche_requests_status   ON niche_requests(status);
CREATE INDEX idx_niche_requests_code     ON niche_requests(unique_code);
CREATE INDEX idx_niche_requests_active   ON niche_requests(status)
  WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS platform_config (
  key         VARCHAR(100) PRIMARY KEY,
  value       VARCHAR(500) NOT NULL,
  updated_at  TIMESTAMP DEFAULT now()
);

INSERT INTO platform_config (key, value) VALUES
  ('TARIF_UNITAIRE_CENTS', '2500')
ON CONFLICT (key) DO NOTHING;
