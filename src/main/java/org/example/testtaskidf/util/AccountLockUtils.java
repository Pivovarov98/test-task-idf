package org.example.testtaskidf.util;

import org.example.testtaskidf.model.ExpenseCategory;

/** Collision-free advisory lock identifiers for ten-digit accounts and the two expense categories. */
public final class AccountLockUtils {
    private AccountLockUtils() {
    }

    public static long key(String account, ExpenseCategory category) {
        if (account == null || !account.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Account must contain exactly 10 digits");
        }
        return Long.parseLong(account) * 2 + switch (category) {
            case ExpenseCategory.Product ignored -> 0;
            case ExpenseCategory.ServiceExpense ignored -> 1;
        };
    }
}
