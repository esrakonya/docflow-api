CREATE TABLE plans (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    monthly_quota INTEGER NOT NULL,
    stripe_price_id VARCHAR(100) UNIQUE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE processed_stripe_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100),
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE api_clients ADD COLUMN plan_id UUID REFERENCES plans(id);
ALTER TABLE api_clients ADD COLUMN stripe_customer_id VARCHAR(100) UNIQUE;
ALTER TABLE api_clients ADD COLUMN stripe_subscription_id VARCHAR(100);

CREATE INDEX idx_api_clients_plan_id ON api_clients(plan_id);
CREATE INDEX idx_api_clients_stripe_customer ON api_clients(stripe_customer_id);

INSERT INTO plans (id, name, monthly_quota, stripe_price_id) VALUES
(gen_random_uuid(), 'FREE', 100, NULL),
(gen_random_uuid(), 'PRO', 1000, 'price_pro_real_id_placeholder'),
(gen_random_uuid(), 'ENTERPRISE', 10000, 'price_ent_real_id_placeholder');


UPDATE api_clients
SET plan_id = (SELECT id FROM plans WHERE name = 'FREE')
WHERE plan_id IS NULL;

ALTER TABLE api_clients ALTER COLUMN plan_id SET NOT NULL;
ALTER TABLE api_clients DROP COLUMN monthly_quota;