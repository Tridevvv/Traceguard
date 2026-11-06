package com.traceguard.report;

import com.traceguard.audit.AuditService;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.CurrentUser;
import com.traceguard.auth.Role;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.NotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ReportService reports;
    private final CurrentUser currentUser;
    private final ProductRepository products;
    private final AuditService audit;
    private final Clock clock;

    public ReportController(ReportService reports, CurrentUser currentUser, ProductRepository products,
                            AuditService audit, Clock clock) {
        this.reports = reports;
        this.currentUser = currentUser;
        this.products = products;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Audit reports for admins and auditors.
     * type: actions (user activity, default), alerts or events (the ledger). format: csv (default) or pdf.
     * from/to: UTC dates, inclusive; default is the last 30 days.
     */
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ResponseEntity<byte[]> audit(@RequestParam(defaultValue = "actions") String type,
                                        @RequestParam(defaultValue = "csv") String format,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        boolean pdf = isPdf(format);
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(29);
        ReportTable table = switch (type) {
            case "actions" -> reports.userActions(start, end);
            case "alerts" -> reports.alertReport(start, end);
            case "events" -> reports.ledgerReport(start, end);
            default -> throw new BadRequestException("type must be actions, alerts or events");
        };
        audit.record("REPORT_EXPORT", type + " " + start + ".." + end + " " + (pdf ? "pdf" : "csv"));
        return respond(table, pdf, "traceguard-" + type + "-" + start + "_" + end);
    }

    /** History of one product (tag): verdict, reasons and timeline. A manufacturer may only export its own tags. */
    @GetMapping("/tags/{tagId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANUFACTURER')")
    public ResponseEntity<byte[]> tagHistory(@PathVariable String tagId, @RequestParam(defaultValue = "csv") String format) {
        boolean pdf = isPdf(format);
        AppUser me = currentUser.get();
        if (me.getRole() == Role.MANUFACTURER
                && !products.organizationOfTag(tagId).filter(o -> o.equals(me.getOrganizationId())).isPresent()) {
            throw new NotFoundException("Tag " + tagId + " not found"); // same answer as unknown, nothing is revealed
        }
        ReportTable table = reports.tagHistory(tagId);
        audit.record("REPORT_EXPORT", "tag " + tagId + " " + (pdf ? "pdf" : "csv"));
        return respond(table, pdf, "traceguard-tag-" + tagId.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    private ResponseEntity<byte[]> respond(ReportTable table, boolean pdf, String baseName) {
        byte[] body = pdf ? PdfExporter.toPdf(table, Instant.now(clock)) : CsvExporter.toCsv(table);
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(baseName + (pdf ? ".pdf" : ".csv")).build().toString())
                .body(body);
    }

    private static boolean isPdf(String format) {
        return switch (format) {
            case "pdf" -> true;
            case "csv" -> false;
            default -> throw new BadRequestException("format must be csv or pdf");
        };
    }

    private LocalDate today() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }

}
