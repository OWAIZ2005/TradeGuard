package com.tradeguard.csv;

/** The file itself is unusable as a trade CSV (bad headers, binary content, broken quoting). */
public class CsvFormatException extends RuntimeException {

    private final String code;

    public CsvFormatException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CsvFormatException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
