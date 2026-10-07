-- PostgreSQL does not index foreign key columns on its own: without these, every DELETE/UPDATE on
-- the parent table scans the child table to check the constraint, and joins on them are sequential.
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_id ON chat_messages (sender_id);
CREATE INDEX IF NOT EXISTS idx_content_comments_parent_id ON content_comments (parent_id);
CREATE INDEX IF NOT EXISTS idx_content_comments_user_id ON content_comments (user_id);
CREATE INDEX IF NOT EXISTS idx_content_comments_hidden_by ON content_comments (hidden_by);
CREATE INDEX IF NOT EXISTS idx_content_reports_reporter_id ON content_reports (reporter_id);
CREATE INDEX IF NOT EXISTS idx_content_shares_user_id ON content_shares (user_id);
CREATE INDEX IF NOT EXISTS idx_mp_review_helpful_votes_user_id ON marketplace_product_review_helpful_votes (user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_created_by ON conversations (created_by);
CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_id ON direct_messages (sender_id);
CREATE INDEX IF NOT EXISTS idx_conversation_invites_created_by ON conversation_invites (created_by);
CREATE INDEX IF NOT EXISTS idx_call_sessions_initiator_id ON call_sessions (initiator_id);
CREATE INDEX IF NOT EXISTS idx_creator_profile_visits_viewer_user_id ON creator_profile_visits (viewer_user_id);
CREATE INDEX IF NOT EXISTS idx_creator_portfolio_posts_content_post_id ON creator_portfolio_posts (content_post_id);
