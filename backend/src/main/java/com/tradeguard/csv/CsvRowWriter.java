package com.tradeguard.csv;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Writes RFC 4180 CSV. Text values are escaped and neutralised against spreadsheet formula injection:
 * a text cell starting with = + - @ tab or CR is prefixed with an apostrophe so Excel/Sheets treat it as text.
 * Numbers ({@link Number}) and timestamps are written verbatim because they come from typed database columns.
 */
public final class CsvRowWriter {

    private final Writer out;

    public CsvRowWriter(Writer out) {
        this.out = out;
    }

    public void writeRow(Object... values) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(format(values[i]));
        }
        line.append("\r\n");
        out.write(line.toString());
    }

    public void flush() throws IOException {
        out.flush();
    }

    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            return plain(d);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Instant t) {
            return t.toString();
        }
        return escape(neutraliseFormula(value.toString()));
    }

    /** Plain notation without exponent or database padding zeros, but always at least 2 decimals (100.50). */
    static String plain(BigDecimal d) {
        BigDecimal n = d.stripTrailingZeros();
        if (n.scale() < 2) {
            n = n.setScale(2);
        }
        return n.toPlainString();
    }

    static String neutraliseFormula(String s) {
        if (!s.isEmpty()) {
            char c = s.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
                return "'" + s;
            }
        }
        return s;
    }

    static String escape(String s) {
        boolean needsQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0 || s.startsWith(" ") || s.endsWith(" ");
        if (!needsQuotes) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
