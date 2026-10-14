package com.tradeguard.repository;

/** Where an already-imported VALID trade id lives (used in duplicate error messages). */
public record ExistingTradeRef(String tradeId, Long batchId, int rowNumber) {
}
