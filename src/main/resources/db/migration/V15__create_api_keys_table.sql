CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES api_clients(id),
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_keys_client_id ON api_keys(client_id);

INSERT INTO api_keys(id, client_id, key_hash, label, active, created_at)
SELECT gen_random_uuid(), id, api_key_hash, 'Legacy Key', true, CURRENT_TIMESTAMP
FROM api_clients
WHERE api_key_hash IS NOT NULL;

ALTER TABLE api_clients ALTER COLUMN api_key_hash DROP NOT NULL;