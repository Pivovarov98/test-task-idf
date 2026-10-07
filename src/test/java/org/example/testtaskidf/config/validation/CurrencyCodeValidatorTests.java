package org.example.testtaskidf.config.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyCodeValidatorTests {
    private final CurrencyCodeValidator validator = new CurrencyCodeValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"KZT", "USD", "EUR", "BYN"})
    void acceptsIsoCurrenciesAndLeavesNullToNotNullConstraint(String code) {
        assertThat(validator.isValid(code, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABC", "kzt", "US", "USDD", " USD", "USD "})
    void rejectsInvalidCurrencies(String code) {
        assertThat(validator.isValid(code, null)).isFalse();
    }
}
