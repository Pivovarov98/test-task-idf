package org.example.testtaskidf.util;

import org.example.testtaskidf.model.ExpenseCategory;

public final class ExpenseCategoryUtils {
    private ExpenseCategoryUtils() {
    }

    public static ExpenseCategory fromCode(String code) {
        return switch (code) {
            case "product" -> new ExpenseCategory.Product();
            case "service" -> new ExpenseCategory.ServiceExpense();
            default -> throw new IllegalArgumentException("Unsupported expense category");
        };
    }

    public static String toCode(ExpenseCategory category) {
        return switch (category) {
            case ExpenseCategory.Product product -> "product";
            case ExpenseCategory.ServiceExpense service -> "service";
        };
    }
}
