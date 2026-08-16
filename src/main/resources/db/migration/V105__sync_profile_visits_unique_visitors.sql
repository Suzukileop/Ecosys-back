-- Align denormalized profile_visits with unique rows in creator_profile_visits
-- (one row per visitor_key). Fixes inflated counters from historical per-reload increments.

UPDATE creator_profiles cp
SET profile_visits = sub.cnt
FROM (
    SELECT creator_user_id, COUNT(*)::int AS cnt
    FROM creator_profile_visits
    GROUP BY creator_user_id
) sub
WHERE cp.user_id = sub.creator_user_id
  AND COALESCE(cp.profile_visits, 0) <> sub.cnt;

UPDATE creator_profiles cp
SET profile_visits = 0
WHERE COALESCE(cp.profile_visits, 0) > 0
  AND NOT EXISTS (
      SELECT 1 FROM creator_profile_visits v WHERE v.creator_user_id = cp.user_id
  );
