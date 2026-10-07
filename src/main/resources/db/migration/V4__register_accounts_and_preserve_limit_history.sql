CREATE TABLE accounts (
    account_number VARCHAR(10) PRIMARY KEY CHECK (account_number ~ '^[0-9]{10}$'),
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO accounts (account_number, created_at)
SELECT account, MIN(created_at)
FROM (
    SELECT account_from AS account, received_at AS created_at FROM transactions
    UNION ALL
    SELECT account, established_at AS created_at FROM expense_limits
) existing_accounts
GROUP BY account;

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_source_account
    FOREIGN KEY (account_from) REFERENCES accounts(account_number);
ALTER TABLE expense_limits ADD CONSTRAINT fk_expense_limits_account
    FOREIGN KEY (account) REFERENCES accounts(account_number);
ALTER TABLE expense_limits DROP CONSTRAINT uq_expense_limits_account_category_time;
ALTER TABLE expense_limits ADD COLUMN revision BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE INDEX idx_expense_limits_account_category_history
    ON expense_limits (account, expense_category, established_at DESC, revision DESC);

CREATE FUNCTION reject_limit_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Expense limit history cannot be updated';
END;
$$;

CREATE TRIGGER expense_limits_append_only BEFORE UPDATE ON expense_limits
    FOR EACH ROW EXECUTE FUNCTION reject_limit_update();
