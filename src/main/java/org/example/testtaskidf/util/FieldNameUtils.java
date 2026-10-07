package org.example.testtaskidf.util;

import java.util.Locale;

public final class FieldNameUtils {
    private FieldNameUtils() {
    }

    public static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
