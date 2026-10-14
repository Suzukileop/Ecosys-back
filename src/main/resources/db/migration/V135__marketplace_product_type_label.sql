-- "Other" products get the creator's own type name. The enum value was renamed AUTRE -> OTHER to
-- match the API (the frontend always sent OTHER, which the backend rejected).
ALTER TABLE marketplace_products ADD COLUMN type_label VARCHAR(60);
UPDATE marketplace_products SET type = 'OTHER' WHERE type = 'AUTRE';
