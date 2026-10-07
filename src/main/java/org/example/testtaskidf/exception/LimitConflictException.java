package org.example.testtaskidf.exception;

/** Expected conflict while establishing a new limit. */
public class LimitConflictException extends RuntimeException {
    private final String code;

    public LimitConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
