package org.example.testtaskidf.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FieldNameUtilsTests {
    @ParameterizedTest
    @CsvSource({"accountFrom,account_from", "accountTo,account_to", "currencyShortname,currency_shortname",
            "expenseCategory,expense_category", "receivedAt,received_at", "sum,sum", "datetime,datetime",
            "account_from,account_from", "ID,id"})
    void convertsValidationFieldNames(String input, String expected) {
        assertThat(FieldNameUtils.toSnakeCase(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a"})
    void preservesEmptyAndSingleLetterNames(String input) {
        assertThat(FieldNameUtils.toSnakeCase(input)).isEqualTo(input);
    }
}
