CREATE TABLE expense_limits (
    id UUID PRIMARY KEY,
    account VARCHAR(10) NOT NULL,
    expense_category VARCHAR(7) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    established_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_expense_limits_account CHECK (account ~ '^[0-9]{10}$'),
    CONSTRAINT chk_expense_limits_category CHECK (expense_category IN ('product', 'service')),
    CONSTRAINT chk_expense_limits_amount CHECK (amount >= 0 AND amount <> 'NaN'::NUMERIC),
    CONSTRAINT chk_expense_limits_currency CHECK (currency = 'USD'),
    CONSTRAINT uq_expense_limits_account_category_time UNIQUE (account, expense_category, established_at)
);
