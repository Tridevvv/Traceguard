package com.traceguard.report;

import java.util.List;

/** A report as plain data, so the same content can be rendered as CSV or PDF. */
public record ReportTable(String title, List<String> notes, List<String> headers, List<List<String>> rows) {}
