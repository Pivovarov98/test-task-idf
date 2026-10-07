package org.example.testtaskidf.util;

import java.util.Locale;

/** Converts Java field names into the names used in API validation errors. */
public final class FieldNameUtils {
    private FieldNameUtils() {
    }

    /**
     * Inserts underscores at lowercase-to-uppercase boundaries and lowercases with Locale.ROOT.
     *
     * @param name non-null Java field name
     * @return snake_case field name
     * @throws NullPointerException if name is null
     */
    public static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
