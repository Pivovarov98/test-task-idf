package org.example.testtaskidf.config.validation;

import java.util.Currency;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validates case-sensitive ISO 4217 codes against currencies supported by the Java runtime. */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {
    /**
     * Checks currency support; null is accepted so that NotNull handles required values separately.
     *
     * @param value currency code to validate
     * @param context Bean Validation context
     * @return true for null or a supported code
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Currency.getInstance(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
