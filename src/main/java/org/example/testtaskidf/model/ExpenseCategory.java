package org.example.testtaskidf.model;

public sealed interface ExpenseCategory permits ExpenseCategory.Product, ExpenseCategory.ServiceExpense {
    record Product() implements ExpenseCategory {
    }

    record ServiceExpense() implements ExpenseCategory {
    }
}
