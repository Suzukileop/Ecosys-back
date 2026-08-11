CREATE TABLE creator_storefronts (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id              UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  subdomain               VARCHAR(100) NOT NULL UNIQUE,
  custom_domain           VARCHAR(255),
  theme_colors            JSONB NOT NULL DEFAULT '{}'::jsonb,
  banner_url              VARCHAR(500),
  logo_url                VARCHAR(500),
  custom_bio              TEXT,
  plan                    VARCHAR(20) NOT NULL DEFAULT 'BASIC',
  is_active               BOOLEAN NOT NULL DEFAULT true,
  subscription_expires_at TIMESTAMP,
  view_count              INT NOT NULL DEFAULT 0,
  created_at              TIMESTAMP NOT NULL DEFAULT now(),
  updated_at              TIMESTAMP
);

CREATE INDEX idx_creator_storefronts_subdomain ON creator_storefronts(subdomain);
CREATE INDEX idx_creator_storefronts_plan ON creator_storefronts(plan) WHERE is_active = true;
