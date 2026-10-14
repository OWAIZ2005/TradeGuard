package com.tradeguard.web.dto;

public record ErrorDto(
        Long tradePk,
        Long batchId,
        int rowNumber,
        String tradeId,
        String field,
        String code,
        String category,
        String message,
        String rejectedValue) {
}
