package com.tradeguard.validation;

/**
 * One CSV record exactly as read from the file (all text). {@code rowNumber} is the spreadsheet row number
 * (header = row 1, first data row = row 2). A record with the wrong number of columns is flagged malformed.
 */
public record RawTradeRecord(
        int rowNumber,
        String tradeId,
        String instrument,
        String timestamp,
        String side,
        String price,
        String quantity,
        String currency,
        String traderId,
        String rawRecord,
        String malformedMessage) {

    public boolean malformed() {
        return malformedMessage != null;
    }

    public static RawTradeRecord of(int rowNumber, String tradeId, String instrument, String timestamp, String side,
                                    String price, String quantity, String currency, String traderId) {
        return new RawTradeRecord(rowNumber, tradeId, instrument, timestamp, side, price, quantity, currency,
                traderId, String.join(",", nz(tradeId), nz(instrument), nz(timestamp), nz(side), nz(price),
                nz(quantity), nz(currency), nz(traderId)), null);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
