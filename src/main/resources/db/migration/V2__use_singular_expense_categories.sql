ALTER TABLE transactions DROP CONSTRAINT chk_transactions_category;

UPDATE transactions
SET expense_category = CASE expense_category
    WHEN 'products' THEN 'product'
    WHEN 'services' THEN 'service'
END
WHERE expense_category IN ('products', 'services');

ALTER TABLE transactions
    ALTER COLUMN expense_category TYPE VARCHAR(7),
    ADD CONSTRAINT chk_transactions_category CHECK (expense_category IN ('product', 'service'));
