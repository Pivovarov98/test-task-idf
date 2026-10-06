CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_from VARCHAR(10) NOT NULL,
    account_to VARCHAR(10) NOT NULL,
    currency_shortname VARCHAR(3) NOT NULL,
    sum NUMERIC(19, 2) NOT NULL,
    expense_category VARCHAR(8) NOT NULL,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transactions_account_from CHECK (account_from ~ '^[0-9]{10}$'),
    CONSTRAINT chk_transactions_account_to CHECK (account_to ~ '^[0-9]{10}$'),
    CONSTRAINT chk_transactions_currency CHECK (currency_shortname ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_transactions_sum CHECK (sum > 0 AND sum <> 'NaN'::NUMERIC),
    CONSTRAINT chk_transactions_category CHECK (expense_category IN ('products', 'services'))
);

CREATE INDEX idx_transactions_account_category_datetime
    ON transactions (account_from, expense_category, datetime);
