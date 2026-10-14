package com.tradeguard.repository;

import com.tradeguard.domain.ErrorCode;

public interface ErrorCodeCount {
    ErrorCode getCode();

    long getOccurrences();

    long getAffectedRows();
}
