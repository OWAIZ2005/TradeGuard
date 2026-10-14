package com.tradeguard.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Expected, client-facing failure. Carries the HTTP status and a stable machine-readable code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<ApiError.Issue> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<ApiError.Issue> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String what, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " was not found.");
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public List<ApiError.Issue> details() { return details; }
}
