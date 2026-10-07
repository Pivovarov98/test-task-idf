package org.example.testtaskidf.util;

import org.example.testtaskidf.model.ExpenseCategory;

/** Converts expense categories between domain values and API/database codes. */
public final class ExpenseCategoryUtils {
    private ExpenseCategoryUtils() {
    }

    /**
     * Resolves a case-sensitive category code without trimming or normalization.
     *
     * @param code product or service
     * @return corresponding domain category
     * @throws IllegalArgumentException for an unsupported code
     * @throws NullPointerException if code is null
     */
    public static ExpenseCategory fromCode(String code) {
        return switch (code) {
            case "product" -> new ExpenseCategory.Product();
            case "service" -> new ExpenseCategory.ServiceExpense();
            default -> throw new IllegalArgumentException("Unsupported expense category");
        };
    }

    /**
     * Returns the stable API/database code for a category.
     *
     * @param category non-null domain category
     * @return product or service
     * @throws NullPointerException if category is null
     */
    public static String toCode(ExpenseCategory category) {
        return switch (category) {
            case ExpenseCategory.Product product -> "product";
            case ExpenseCategory.ServiceExpense service -> "service";
        };
    }
}
