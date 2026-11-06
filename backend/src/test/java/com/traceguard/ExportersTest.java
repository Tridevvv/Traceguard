package com.traceguard;

import com.traceguard.report.CsvExporter;
import com.traceguard.report.PdfExporter;
import com.traceguard.report.ReportTable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for the CSV and PDF renderers. */
class ExportersTest {

    static ReportTable table(List<List<String>> rows) {
        return new ReportTable("Test report", List.of("Period: x"), List.of("Time", "User", "Action"), rows);
    }

    static String csv(ReportTable t) {
        return new String(CsvExporter.toCsv(t), StandardCharsets.UTF_8);
    }

    @Test
    void csvHasBomHeaderAndCrlfLines() {
        String out = csv(table(List.of(List.of("t1", "a@b.com", "LOGIN"))));
        assertTrue(out.startsWith("﻿Time,User,Action\r\n"), out);
        assertTrue(out.endsWith("t1,a@b.com,LOGIN\r\n"), out);
    }

    @Test
    void csvQuotesCommasQuotesAndNewlines() {
        String out = csv(table(List.of(List.of("t", "Smith, John", "said \"hi\"\nnow"))));
        assertTrue(out.contains("\"Smith, John\""), out);
        assertTrue(out.contains("\"said \"\"hi\"\"\nnow\""), out);
    }

    @Test
    void csvNeutralisesSpreadsheetFormulas() {
        String out = csv(table(List.of(List.of("t", "=HYPERLINK(\"http://evil\")", "@SUM(A1)"),
                List.of("t", "+1+1", "-2+3"))));
        assertTrue(out.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), out);
        assertTrue(out.contains("'@SUM(A1)"), out);
        assertTrue(out.contains("'+1+1"), out);
        assertTrue(out.contains("'-2+3"), out);
    }

    @Test
    void csvTreatsNullAsEmpty() {
        List<String> row = new ArrayList<>(List.of("t", "", "x"));
        row.set(1, null);
        assertTrue(csv(table(List.of(row))).contains("t,,x\r\n"));
    }

    @Test
    void csvOfAnEmptyTableIsJustTheHeader() {
        assertEquals("﻿Time,User,Action\r\n", csv(table(List.of())));
    }

    @Test
    void pdfIsAValidPdfDocument() {
        byte[] pdf = PdfExporter.toPdf(table(List.of(List.of("t1", "a@b.com", "LOGIN"))), Instant.parse("2026-11-01T00:00:00Z"));
        assertTrue(pdf.length > 500);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).contains("%%EOF"));
    }

    @Test
    void pdfHandlesManyRowsAndOddText() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(List.of("2026-11-01T00:00:" + i, "user" + i + "@x.com", i % 7 == 0 ? "Ünïcödé ✓ 漢字 <&>" : "SCAN"));
        }
        byte[] pdf = PdfExporter.toPdf(table(rows), Instant.now());
        assertTrue(pdf.length > 5_000, "multi-page document expected, was " + pdf.length);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void pdfHandlesAnEmptyTable() {
        assertDoesNotThrow(() -> PdfExporter.toPdf(table(List.of()), Instant.now()));
    }
}
