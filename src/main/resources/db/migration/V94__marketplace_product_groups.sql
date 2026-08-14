CREATE TABLE marketplace_product_groups (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        VARCHAR(120) NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  deleted_at  TIMESTAMP,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP
);

CREATE INDEX idx_marketplace_product_groups_creator
  ON marketplace_product_groups(creator_id)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_marketplace_product_groups_creator_name
  ON marketplace_product_groups(creator_id, lower(name))
  WHERE deleted_at IS NULL;

CREATE TABLE marketplace_product_group_items (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    UUID NOT NULL REFERENCES marketplace_product_groups(id) ON DELETE CASCADE,
  product_id  UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
  sort_order  INT NOT NULL DEFAULT 0,
  UNIQUE (group_id, product_id)
);

CREATE INDEX idx_marketplace_product_group_items_group
  ON marketplace_product_group_items(group_id);

CREATE INDEX idx_marketplace_product_group_items_product
  ON marketplace_product_group_items(product_id);
