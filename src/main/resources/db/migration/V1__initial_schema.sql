CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- API_CLIENTS
CREATE TABLE api_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key_hash VARCHAR(255) NOT NULL UNIQUE,
    company_name VARCHAR(255) NOT NULL,
    plan_tier VARCHAR(50) DEFAULT 'free',
    monthly_quota INTEGER DEFAULT 100,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- DOCUMENTS
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES api_clients(id),
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    callback_url VARCHAR(500),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- EXTRACTED DATA (AI RESULTS)
CREATE TABLE extracted_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID REFERENCES documents(id) UNIQUE,
    vendor_name VARCHAR(255),
    invoice_number VARCHAR(100),
    invoice_date DATE,
    due_date DATE,
    total_amount NUMERIC(14,2),
    tax_amount NUMERIC(14,2),
    currency VARCHAR(10),
    overall_confidence NUMERIC(4,3),
    raw_llm_response JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- DOCUMENT_LINE_ITEMS
CREATE TABLE document_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extracted_data_id UUID REFERENCES extracted_data(id),
    description TEXT,
    quantity NUMERIC(14,2),
    unit_price NUMERIC(14,2),
    line_total NUMERIC(14,2)
);