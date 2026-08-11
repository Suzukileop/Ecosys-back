CREATE TABLE content_access_logs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_id   UUID NOT NULL REFERENCES marketplace_purchases(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id    UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
  access_mode   VARCHAR(20) NOT NULL,
  ip_address    VARCHAR(45),
  user_agent    TEXT,
  accessed_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_access_logs_purchase ON content_access_logs(purchase_id);
CREATE INDEX idx_content_access_logs_user ON content_access_logs(user_id);
CREATE INDEX idx_content_access_logs_product ON content_access_logs(product_id);
