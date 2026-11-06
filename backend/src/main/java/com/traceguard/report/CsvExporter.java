package com.traceguard.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** RFC 4180 CSV with a UTF-8 byte-order mark (so Excel reads accents correctly) and CRLF line ends. */
public final class CsvExporter {

    private CsvExporter() {}

    public static byte[] toCsv(ReportTable table) {
        StringBuilder sb = new StringBuilder("﻿");
        appendRow(sb, table.headers());
        table.rows().forEach(r -> appendRow(sb, r));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    /**
     * Quotes the cell when needed. Cells starting with = + - @ (or tab / CR) are prefixed with an apostrophe:
     * they hold user-supplied text such as tag IDs, and a spreadsheet would otherwise run them as a formula
     * (CSV injection).
     */
    static String escape(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
