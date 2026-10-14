package com.tradeguard.web;

import java.time.Instant;
import java.util.List;

/** The single error body returned by every failing endpoint. */
public record ApiError(Instant timestamp, int status, String error, String code, String message, String path,
                       List<Issue> details) {

    public record Issue(String field, String message) {
    }
}
