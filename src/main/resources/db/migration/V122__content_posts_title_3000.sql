-- The post headline is now the main post text (X/LinkedIn-style composer): allow up to 3000 characters.
ALTER TABLE content_posts ALTER COLUMN title TYPE VARCHAR(3000);
