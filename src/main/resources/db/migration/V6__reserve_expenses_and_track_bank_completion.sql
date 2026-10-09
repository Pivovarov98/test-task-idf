ALTER TABLE transactions ADD COLUMN operation_sequence BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE transactions ADD COLUMN conversion_rate_date DATE;
UPDATE transactions SET conversion_rate_date = LEAST((datetime AT TIME ZONE 'UTC')::date,
    (received_at AT TIME ZONE 'UTC')::date - 1);
UPDATE transactions SET conversion_rate_date = conversion_rate_date - CASE
    WHEN EXTRACT(ISODOW FROM conversion_rate_date) = 6 THEN 1
    WHEN EXTRACT(ISODOW FROM conversion_rate_date) = 7 THEN 2 ELSE 0 END;
ALTER TABLE transactions ALTER COLUMN conversion_rate_date SET NOT NULL;
CREATE FUNCTION assign_conversion_date() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.conversion_rate_date IS NULL THEN
        NEW.conversion_rate_date := LEAST((NEW.datetime AT TIME ZONE 'UTC')::date,
            (NEW.received_at AT TIME ZONE 'UTC')::date - 1);
        NEW.conversion_rate_date := NEW.conversion_rate_date - CASE
            WHEN EXTRACT(ISODOW FROM NEW.conversion_rate_date) = 6 THEN 1
            WHEN EXTRACT(ISODOW FROM NEW.conversion_rate_date) = 7 THEN 2 ELSE 0 END;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER transactions_conversion_date BEFORE INSERT ON transactions
    FOR EACH ROW EXECUTE FUNCTION assign_conversion_date();
ALTER TABLE transactions ADD COLUMN operation_status VARCHAR(12) NOT NULL DEFAULT 'PROCESSING'
    CHECK (operation_status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT'));
ALTER TABLE transactions ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE transactions ADD COLUMN limit_exceeded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN limit_check_status VARCHAR(9) NOT NULL DEFAULT 'PENDING'
    CHECK (limit_check_status IN ('PENDING', 'COMPLETED'));
ALTER TABLE transactions ADD COLUMN reserved_usd NUMERIC;
ALTER TABLE transactions ADD COLUMN reservation_active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN exceeded_at_reservation BOOLEAN;
ALTER TABLE transactions ADD COLUMN already_exceeded_before BOOLEAN;
ALTER TABLE transactions ADD COLUMN applied_limit_id UUID REFERENCES expense_limits(id);
ALTER TABLE transactions ADD COLUMN applied_limit_usd NUMERIC;
ALTER TABLE transactions ADD COLUMN bank_unavailable_since TIMESTAMPTZ;
ALTER TABLE transactions ADD COLUMN next_bank_poll_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE transactions ADD COLUMN bank_stub_status VARCHAR(12) NOT NULL DEFAULT 'PROCESSING'
    CHECK (bank_stub_status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'ERROR'));
ALTER TABLE transactions ADD CONSTRAINT chk_reservation_active
    CHECK (NOT reservation_active OR (reserved_usd IS NOT NULL AND reserved_usd >= 0));
CREATE INDEX idx_operation_reservation_order ON transactions
    (account_from, expense_category, datetime, operation_sequence);
CREATE INDEX idx_operation_poll ON transactions (next_bank_poll_at) WHERE operation_status = 'PROCESSING';
