-- Sales counter for product cards (public social proof)

ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS sales_count INT NOT NULL DEFAULT 0;

UPDATE marketplace_products p
SET sales_count = COALESCE((
    SELECT COUNT(*)::INT
    FROM marketplace_purchases mp
    WHERE mp.product_id = p.id
      AND mp.payment_status = 'COMPLETED'
), 0);
