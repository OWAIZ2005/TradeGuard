package com.tradeguard.web.dto;

/** occurrences = number of error records, affectedRows = distinct rows carrying that error. */
public record ErrorBreakdownItem(String code, String category, long occurrences, long affectedRows) {
}
