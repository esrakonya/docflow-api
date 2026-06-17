CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES api_clients(id),
    usage_month VARCHAR(7) NOT NULL,
    request_count INTEGER DEFAULT 0,
    UNIQUE(client_id, usage_month)
)