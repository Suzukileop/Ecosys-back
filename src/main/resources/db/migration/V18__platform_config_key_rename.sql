-- "key" est réservé en H2 ; alignement avec les tests en mémoire.
ALTER TABLE platform_config RENAME COLUMN key TO config_key;
