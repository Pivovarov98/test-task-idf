-- Only completed successful exceedances are eligible for the client list.
CREATE INDEX idx_successful_limit_exceedances ON transactions
    (account_from, datetime DESC, operation_sequence DESC)
    WHERE operation_status = 'SUCCEEDED' AND limit_check_status = 'COMPLETED' AND limit_exceeded = TRUE;
