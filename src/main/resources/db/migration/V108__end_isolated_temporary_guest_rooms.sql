-- Close isolated temporary rooms left from the previous invite flow.
-- Guests must chat in the permanent A↔B conversation so hosts see their messages.

UPDATE conversation_participants cp
SET left_at = NOW(),
    expires_at = NOW()
FROM conversations c
WHERE cp.conversation_id = c.id
  AND c.temporary_session = TRUE
  AND cp.role = 'GUEST'
  AND cp.left_at IS NULL;

UPDATE conversation_invites ci
SET status = 'CANCELLED'
FROM conversations c
WHERE ci.conversation_id = c.id
  AND c.temporary_session = TRUE
  AND ci.status IN ('PENDING', 'ACCEPTED')
  AND ci.invitee_id IS NOT NULL;
