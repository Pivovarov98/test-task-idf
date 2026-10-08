CREATE TABLE exchange_rate_snapshots (
    requested_date DATE PRIMARY KEY,
    rate_date DATE NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    provider VARCHAR(32) NOT NULL CHECK (provider = 'OPEN_EXCHANGE_RATES'),
    CHECK (rate_date <= requested_date)
);

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    requested_date DATE NOT NULL REFERENCES exchange_rate_snapshots(requested_date),
    base_currency VARCHAR(3) NOT NULL CHECK (base_currency ~ '^[A-Z]{3}$'),
    quote_currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (quote_currency = 'USD'),
    units_per_usd NUMERIC NOT NULL CHECK (units_per_usd > 0 AND units_per_usd <> 'NaN'::NUMERIC),
    UNIQUE (requested_date, base_currency)
);

CREATE TABLE exchange_rate_jobs (
    requested_date DATE PRIMARY KEY,
    status VARCHAR(8) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'BLOCKED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(64)
);

ALTER TABLE transactions ADD COLUMN amount_usd NUMERIC;
ALTER TABLE transactions ADD COLUMN conversion_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (conversion_status IN ('PENDING', 'COMPLETED', 'UNSUPPORTED_CURRENCY'));
ALTER TABLE transactions ADD COLUMN exchange_rate_id UUID REFERENCES exchange_rates(id);
ALTER TABLE transactions ADD COLUMN conversion_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_conversion CHECK (
    (conversion_status = 'COMPLETED' AND amount_usd IS NOT NULL AND amount_usd >= 0
        AND amount_usd <> 'NaN'::NUMERIC AND (currency_shortname = 'USD' OR exchange_rate_id IS NOT NULL))
    OR (conversion_status <> 'COMPLETED' AND amount_usd IS NULL AND exchange_rate_id IS NULL)
);
UPDATE transactions SET amount_usd = sum, conversion_status = 'COMPLETED' WHERE currency_shortname = 'USD';
CREATE INDEX idx_transactions_pending_conversion ON transactions (received_at, id)
    WHERE conversion_status = 'PENDING';
