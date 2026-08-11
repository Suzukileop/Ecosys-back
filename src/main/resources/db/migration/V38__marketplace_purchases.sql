CREATE TABLE marketplace_purchases (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id        UUID REFERENCES marketplace_products(id) ON DELETE SET NULL,
  bundle_id         UUID REFERENCES marketplace_bundles(id) ON DELETE SET NULL,
  price_paid_cents  INT NOT NULL CHECK (price_paid_cents >= 0),
  currency          VARCHAR(3) NOT NULL DEFAULT 'EUR',
  payment_status    VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  download_count    INT NOT NULL DEFAULT 0,
  purchased_at      TIMESTAMP NOT NULL DEFAULT now(),
  expires_at        TIMESTAMP,
  CONSTRAINT chk_marketplace_purchase_target CHECK (
    (product_id IS NOT NULL AND bundle_id IS NULL)
    OR (product_id IS NULL AND bundle_id IS NOT NULL)
  )
);

CREATE INDEX idx_marketplace_purchases_buyer ON marketplace_purchases(buyer_id);
CREATE INDEX idx_marketplace_purchases_product ON marketplace_purchases(product_id);
CREATE INDEX idx_marketplace_purchases_bundle ON marketplace_purchases(bundle_id);
