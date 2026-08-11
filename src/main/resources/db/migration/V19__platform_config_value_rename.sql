-- "value" est réservé en H2 pour les schémas générés par Hibernate en tests.
ALTER TABLE platform_config RENAME COLUMN value TO config_value;
