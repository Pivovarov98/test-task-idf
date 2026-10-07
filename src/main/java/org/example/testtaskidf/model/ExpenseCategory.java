package org.example.testtaskidf.model;

/** Closed set of expense categories supported by the API and database. */
public sealed interface ExpenseCategory permits ExpenseCategory.Product, ExpenseCategory.ServiceExpense {
    /** Purchase of a product, represented by the code product. */
    record Product() implements ExpenseCategory {
    }

    /** Purchase of a service, represented by the code service. */
    record ServiceExpense() implements ExpenseCategory {
    }
}
