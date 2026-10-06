package org.example.testtaskidf.util;

public final class UtilityFixtures {
    private UtilityFixtures() {
    }

    public static class StatefulUtils {
        private String value;

        public String getValue() {
            return value;
        }
    }

    public static final class ValidUtils {
        private ValidUtils() {
        }

        public static String normalize(String value) {
            return value.trim();
        }
    }
}
