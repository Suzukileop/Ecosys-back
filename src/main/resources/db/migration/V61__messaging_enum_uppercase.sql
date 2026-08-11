-- Align DB enum string values with Java EnumType.STRING (UPPERCASE names)

UPDATE conversations SET type = 'DIRECT' WHERE LOWER(type) = 'direct';
UPDATE conversations SET type = 'GROUP' WHERE LOWER(type) = 'group';

UPDATE conversation_participants SET role = 'OWNER' WHERE LOWER(role) = 'owner';
UPDATE conversation_participants SET role = 'ADMIN' WHERE LOWER(role) = 'admin';
UPDATE conversation_participants SET role = 'MEMBER' WHERE LOWER(role) = 'member';
UPDATE conversation_participants SET role = 'GUEST' WHERE LOWER(role) = 'guest';

UPDATE direct_messages SET message_type = 'TEXT' WHERE LOWER(message_type) = 'text';
UPDATE direct_messages SET message_type = 'FILE' WHERE LOWER(message_type) = 'file';
UPDATE direct_messages SET message_type = 'SYSTEM' WHERE LOWER(message_type) = 'system';
UPDATE direct_messages SET message_type = 'CALL' WHERE LOWER(message_type) = 'call';

UPDATE conversation_invites SET role = 'GUEST' WHERE LOWER(role) = 'guest';
UPDATE conversation_invites SET role = 'MEMBER' WHERE LOWER(role) = 'member';
UPDATE conversation_invites SET role = 'ADMIN' WHERE LOWER(role) = 'admin';
UPDATE conversation_invites SET role = 'OWNER' WHERE LOWER(role) = 'owner';

UPDATE call_sessions SET call_type = 'VOICE' WHERE LOWER(call_type) = 'voice';
UPDATE call_sessions SET call_type = 'VIDEO' WHERE LOWER(call_type) = 'video';
UPDATE call_sessions SET status = 'RINGING' WHERE LOWER(status) = 'ringing';
UPDATE call_sessions SET status = 'ACTIVE' WHERE LOWER(status) = 'active';
UPDATE call_sessions SET status = 'ENDED' WHERE LOWER(status) = 'ended';
UPDATE call_sessions SET status = 'MISSED' WHERE LOWER(status) = 'missed';

ALTER TABLE conversations ALTER COLUMN type SET DEFAULT 'DIRECT';
ALTER TABLE conversation_participants ALTER COLUMN role SET DEFAULT 'MEMBER';
ALTER TABLE direct_messages ALTER COLUMN message_type SET DEFAULT 'TEXT';
ALTER TABLE conversation_invites ALTER COLUMN role SET DEFAULT 'GUEST';
ALTER TABLE call_sessions ALTER COLUMN call_type SET DEFAULT 'VOICE';
ALTER TABLE call_sessions ALTER COLUMN status SET DEFAULT 'RINGING';
