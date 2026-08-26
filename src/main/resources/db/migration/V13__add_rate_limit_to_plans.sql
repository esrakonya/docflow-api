ALTER TABLE plans ADD COLUMN rate_limit_per_min INTEGER NOT NULL DEFAULT 10;

UPDATE plans SET rate_limit_per_min = 5 WHERE name = 'FREE';
UPDATE plans SET rate_limit_per_min = 60 WHERE name = 'PRO';
UPDATE plans SET rate_limit_per_min = 500 WHERE name = 'ENTERPRISE';