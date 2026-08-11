ALTER TABLE niche_requests RENAME COLUMN stripe_session_id TO vpi_payment_id;
ALTER TABLE niche_requests RENAME COLUMN stripe_subscription_id TO vpi_reference;
