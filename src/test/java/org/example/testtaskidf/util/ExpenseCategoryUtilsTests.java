package org.example.testtaskidf.util;

import org.example.testtaskidf.model.ExpenseCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseCategoryUtilsTests {
    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void convertsSupportedCodesInBothDirections(String code) {
        var category = ExpenseCategoryUtils.fromCode(code);
        assertThat(category).isInstanceOf(code.equals("product")
                ? ExpenseCategory.Product.class : ExpenseCategory.ServiceExpense.class);
        assertThat(ExpenseCategoryUtils.toCode(category)).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "products", "services", "PRODUCT", " product", "other"})
    void rejectsUnsupportedCodes(String code) {
        assertThatThrownBy(() -> ExpenseCategoryUtils.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported expense category");
    }
}
