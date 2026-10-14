-- Posts no longer carry a "tools used" list (removed from the composer, the feed, the post
-- detail and the portfolio Work designs). The column and its data are dropped for good.
ALTER TABLE content_posts DROP COLUMN IF EXISTS tools_used;
