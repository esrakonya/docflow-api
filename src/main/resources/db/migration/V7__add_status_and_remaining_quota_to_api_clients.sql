ALTER TABLE api_clients ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE api_clients ADD COLUMN remaining_quota INTEGER;

UPDATE api_clients
SET remaining_quota = monthly_quota
WHERE remaining_quota IS NULL;