-- Retrait du module écosystème (niches, agent, bots, planification, crédits IA, tarif plateforme).

DROP TABLE IF EXISTS publication_analytics CASCADE;
DROP TABLE IF EXISTS scheduled_posts CASCADE;
DROP TABLE IF EXISTS scheduled_configs CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS niche_requests CASCADE;
DROP TABLE IF EXISTS credit_transactions CASCADE;
DROP TABLE IF EXISTS user_credits CASCADE;
DROP TABLE IF EXISTS platform_config CASCADE;

DELETE FROM user_roles
WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_AGENT');

DELETE FROM roles WHERE name = 'ROLE_AGENT';

DELETE FROM notifications
WHERE type LIKE 'NICHE\_%'
   OR type LIKE 'ECOSYSTEM\_%'
   OR type LIKE 'AGENT\_%'
   OR type LIKE 'DEMO\_%'
   OR type LIKE 'POST\_%'
   OR type IN ('CONTENT_DELIVERED', 'PAYMENT_FAILED');
