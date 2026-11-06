package com.traceguard.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

/** Landscape A4 PDF with a title, notes, a table whose header repeats on every page, and page numbers. */
public final class PdfExporter {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font FOOT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Color HEAD_BACKGROUND = new Color(0x13, 0x30, 0x4F);
    private static final Color STRIPE = new Color(0xF2, 0xF5, 0xF8);

    private PdfExporter() {}

    public static byte[] toPdf(ReportTable table, Instant generatedAt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 40, 44);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageNumbers());
            doc.open();

            doc.add(new Paragraph(table.title(), TITLE));
            doc.add(new Paragraph("TraceGuard  |  generated " + generatedAt + " (UTC)  |  " + table.rows().size() + " row(s)", NOTE));
            for (String note : table.notes()) doc.add(new Paragraph(note, NOTE));
            doc.add(new Paragraph(" ", NOTE));

            int columns = table.headers().size();
            PdfPTable pdf = new PdfPTable(columns);
            pdf.setWidthPercentage(100);
            pdf.setWidths(widths(table));
            pdf.setHeaderRows(1);
            for (String h : table.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEAD));
                cell.setBackgroundColor(HEAD_BACKGROUND);
                cell.setPadding(4);
                pdf.addCell(cell);
            }
            int n = 0;
            for (List<String> row : table.rows()) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, CELL));
                    cell.setPadding(3);
                    if (n % 2 == 1) cell.setBackgroundColor(STRIPE);
                    pdf.addCell(cell);
                }
                n++;
            }
            doc.add(pdf);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    /** Column widths proportional to the longest value (capped), so short columns don't waste space. */
    private static float[] widths(ReportTable table) {
        int columns = table.headers().size();
        float[] w = new float[columns];
        for (int c = 0; c < columns; c++) {
            int longest = table.headers().get(c).length();
            for (List<String> row : table.rows()) {
                String v = row.get(c);
                if (v != null) longest = Math.max(longest, v.length());
            }
            w[c] = Math.min(Math.max(longest, 6), 60);
        }
        return w;
    }

    private static final class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), FOOT), document.right(), document.bottom() - 14, 0);
        }
    }
}
