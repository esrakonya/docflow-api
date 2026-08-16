ALTER TABLE outbox_messages ADD COLUMN retry_count INTEGER DEFAULT 0;
ALTER TABLE outbox_messages ADD COLUMN last_error TEXT;
ALTER TABLE outbox_messages ADD COLUMN failed BOOLEAN DEFAULT FALSE;

UPDATE outbox_messages SET retry_count = 0 WHERE retry_count IS NULL;
UPDATE outbox_messages SET failed = FALSE WHERE failed IS NULL;