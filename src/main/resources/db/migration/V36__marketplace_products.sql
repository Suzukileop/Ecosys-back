CREATE TABLE marketplace_products (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type                    VARCHAR(30) NOT NULL,
  title                   VARCHAR(300) NOT NULL,
  description             TEXT,
  price_cents             INT NOT NULL CHECK (price_cents >= 0),
  currency                VARCHAR(3) NOT NULL DEFAULT 'EUR',
  genre                   VARCHAR(100),
  niche                   VARCHAR(150),
  thumbnail_url           VARCHAR(500),
  demo_type               VARCHAR(20) NOT NULL DEFAULT 'NONE',
  demo_url                VARCHAR(500),
  demo_description        TEXT,
  main_file_r2_key        VARCHAR(500) NOT NULL,
  delivery_mode           VARCHAR(20) NOT NULL DEFAULT 'BOTH',
  license_type            VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',
  compatible_tools        JSONB NOT NULL DEFAULT '[]'::jsonb,
  file_format             VARCHAR(50),
  file_size_mb            INT,
  language                VARCHAR(10),
  version                 VARCHAR(20),
  preview_limit_percent   INT CHECK (preview_limit_percent IS NULL OR preview_limit_percent BETWEEN 0 AND 100),
  max_downloads           INT,
  tags                    JSONB NOT NULL DEFAULT '[]'::jsonb,
  views                   INT NOT NULL DEFAULT 0,
  likes                   INT NOT NULL DEFAULT 0,
  dislikes                INT NOT NULL DEFAULT 0,
  favorites               INT NOT NULL DEFAULT 0,
  comments                INT NOT NULL DEFAULT 0,
  downloads               INT NOT NULL DEFAULT 0,
  shares                  INT NOT NULL DEFAULT 0,
  is_published            BOOLEAN NOT NULL DEFAULT false,
  deleted_at              TIMESTAMP,
  created_at              TIMESTAMP NOT NULL DEFAULT now(),
  updated_at              TIMESTAMP
);

CREATE INDEX idx_marketplace_products_creator ON marketplace_products(creator_id);
CREATE INDEX idx_marketplace_products_published ON marketplace_products(is_published)
  WHERE deleted_at IS NULL;
CREATE INDEX idx_marketplace_products_type ON marketplace_products(type) WHERE deleted_at IS NULL;
CREATE INDEX idx_marketplace_products_genre ON marketplace_products(genre) WHERE deleted_at IS NULL;
