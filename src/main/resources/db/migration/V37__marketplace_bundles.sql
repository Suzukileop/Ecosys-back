CREATE TABLE marketplace_bundles (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title             VARCHAR(300) NOT NULL,
  description       TEXT,
  price_cents       INT NOT NULL CHECK (price_cents >= 0),
  currency          VARCHAR(3) NOT NULL DEFAULT 'EUR',
  thumbnail_url     VARCHAR(500),
  discount_percent  INT CHECK (discount_percent IS NULL OR discount_percent BETWEEN 0 AND 100),
  is_published      BOOLEAN NOT NULL DEFAULT false,
  deleted_at        TIMESTAMP,
  created_at        TIMESTAMP NOT NULL DEFAULT now(),
  updated_at        TIMESTAMP
);

CREATE INDEX idx_marketplace_bundles_creator ON marketplace_bundles(creator_id);
CREATE INDEX idx_marketplace_bundles_published ON marketplace_bundles(is_published)
  WHERE deleted_at IS NULL;

CREATE TABLE marketplace_bundle_items (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bundle_id   UUID NOT NULL REFERENCES marketplace_bundles(id) ON DELETE CASCADE,
  product_id  UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
  sort_order  INT NOT NULL DEFAULT 0,
  UNIQUE (bundle_id, product_id)
);

CREATE INDEX idx_marketplace_bundle_items_bundle ON marketplace_bundle_items(bundle_id);
CREATE INDEX idx_marketplace_bundle_items_product ON marketplace_bundle_items(product_id);

ALTER TABLE marketplace_products
  ADD COLUMN bundle_id UUID REFERENCES marketplace_bundles(id) ON DELETE SET NULL;

CREATE INDEX idx_marketplace_products_bundle ON marketplace_products(bundle_id)
  WHERE bundle_id IS NOT NULL;
