ALTER TABLE extracted_data ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE extracted_data ADD COLUMN updated_at TIMESTAMP;