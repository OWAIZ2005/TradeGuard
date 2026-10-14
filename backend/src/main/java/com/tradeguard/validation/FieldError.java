package com.tradeguard.validation;

import com.tradeguard.domain.ErrorCode;

/** A single validation failure for one field of one row. */
public record FieldError(String field, ErrorCode code, String message, String rejectedValue) {
}
