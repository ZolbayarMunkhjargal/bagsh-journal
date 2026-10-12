package app.kinderjournal.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Saves a report as a Word document.
 *
 * <p>The document is written as WordprocessingML by hand - see
 * {@link OoxmlPackage} for why. Headings become real Word headings so the
 * navigation pane and any table of contents work; tables become real Word tables
 * with a repeating heading row, so a register that runs over a page still shows
 * its column names on the second page.
 */
public final class WordDocument {

    /** A4 portrait with the margins a printed report uses. */
    private static final int PAGE_WIDTH_TWIPS = 11906;
    private static final int PAGE_HEIGHT_TWIPS = 16838;
    private static final int MARGIN_TWIPS = 1134;    // 20mm

    private WordDocument() {
    }

    /** Writes {@code document} to {@code file}, creating folders as needed. */
    public static void write(ReportDocument document, Path file) throws IOException {
        if (document.blocks().isEmpty()) {
            throw new IllegalArgumentException(
                    app.kinderjournal.i18n.Messages.get("format.onlyText"));
        }
        try (OoxmlPackage pkg = new OoxmlPackage(file)) {
            pkg.part("[Content_Types].xml", contentTypes(), true);
            pkg.part("_rels/.rels", packageRels(), false);
            pkg.part("word/document.xml", body(document), false);
            pkg.part("word/styles.xml", styles(), false);
            pkg.part("word/_rels/document.xml.rels", documentRels(), false);
        }
    }

    // ------------------------------------------------------------------- parts

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>""";
    }

    private static String packageRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""";
    }

    private static String documentRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""";
    }

    /**
     * The styles this document uses.
     *
     * <p>Named so Word treats them as its own built-in styles: "heading 1" and
     * friends are recognised by name, which is what makes the navigation pane and
     * the outline level work without any extra markup.
     */
    private static String styles() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:docDefaults>
                    <w:rPrDefault><w:rPr>
                      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
                      <w:sz w:val="22"/><w:szCs w:val="22"/>
                    </w:rPr></w:rPrDefault>
                    <w:pPrDefault><w:pPr><w:spacing w:after="140" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault>
                  </w:docDefaults>
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/><w:qFormat/>
                  </w:style>
                """);
        sb.append(headingStyle("heading 1", "Heading1", 1, 36, "2E7D74", 0));
        sb.append(headingStyle("heading 2", "Heading2", 2, 28, "1F2933", 240));
        sb.append(headingStyle("heading 3", "Heading3", 3, 24, "1F2933", 200));
        sb.append("""
                  <w:style w:type="paragraph" w:styleId="Title">
                    <w:name w:val="Title"/><w:qFormat/>
                    <w:pPr><w:spacing w:after="60"/></w:pPr>
                    <w:rPr><w:b/><w:sz w:val="48"/><w:szCs w:val="48"/></w:rPr>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Subtitle">
                    <w:name w:val="Subtitle"/><w:qFormat/>
                    <w:pPr><w:spacing w:after="240"/></w:pPr>
                    <w:rPr><w:color w:val="64748B"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="ListParagraph">
                    <w:name w:val="List Paragraph"/><w:qFormat/>
                    <w:pPr><w:ind w:left="360"/></w:pPr>
                  </w:style>
                  <w:style w:type="table" w:default="1" w:styleId="TableNormal">
                    <w:name w:val="Normal Table"/>
                    <w:tblPr><w:tblCellMar>
                      <w:top w:w="60" w:type="dxa"/><w:left w:w="108" w:type="dxa"/>
                      <w:bottom w:w="60" w:type="dxa"/><w:right w:w="108" w:type="dxa"/>
                    </w:tblCellMar></w:tblPr>
                  </w:style>
                </w:styles>""");
        return sb.toString();
    }

    private static String headingStyle(String name, String id, int level, int halfPoints,
                                       String colour, int spaceBefore) {
        return """
                  <w:style w:type="paragraph" w:styleId="%s">
                    <w:name w:val="%s"/><w:basedOn w:val="Normal"/><w:qFormat/>
                    <w:pPr><w:outlineLvl w:val="%d"/><w:keepNext/>
                      <w:spacing w:before="%d" w:after="80"/></w:pPr>
                    <w:rPr><w:b/><w:color w:val="%s"/><w:sz w:val="%d"/><w:szCs w:val="%d"/></w:rPr>
                  </w:style>
                """.formatted(id, name, level - 1, spaceBefore, colour, halfPoints, halfPoints);
    }

    // -------------------------------------------------------------------- body

    private static String body(ReportDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                """);

        if (!document.title().isBlank()) {
            sb.append(paragraph("Title", List.of(new ReportDocument.Run(document.title(), false))));
        }
        if (!document.subtitle().isBlank()) {
            sb.append(paragraph("Subtitle",
                    List.of(new ReportDocument.Run(document.subtitle(), false))));
        }

        for (ReportDocument.Block block : document.blocks()) {
            if (block instanceof ReportDocument.Heading heading) {
                sb.append(paragraph("Heading" + Math.min(3, Math.max(1, heading.level())),
                        List.of(new ReportDocument.Run(heading.text(), false))));
            } else if (block instanceof ReportDocument.Paragraph para) {
                sb.append(paragraph(null, para.runs()));
            } else if (block instanceof ReportDocument.Bullets bullets) {
                for (String item : bullets.items()) {
                    sb.append(paragraph("ListParagraph",
                            List.of(new ReportDocument.Run("\u2022  " + item, false))));
                }
            } else if (block instanceof ReportDocument.Table table) {
                sb.append(table(table));
            }
        }

        // The section properties have to be the last thing in the body.
        sb.append("""
                    <w:sectPr>
                      <w:pgSz w:w="%d" w:h="%d"/>
                      <w:pgMar w:top="%d" w:right="%d" w:bottom="%d" w:left="%d"
                               w:header="708" w:footer="708" w:gutter="0"/>
                    </w:sectPr>
                  </w:body>
                </w:document>""".formatted(PAGE_WIDTH_TWIPS, PAGE_HEIGHT_TWIPS,
                MARGIN_TWIPS, MARGIN_TWIPS, MARGIN_TWIPS, MARGIN_TWIPS));
        return sb.toString();
    }

    /** A paragraph made of runs. A newline inside a run becomes its own line. */
    private static String paragraph(String style, List<ReportDocument.Run> runs) {
        StringBuilder sb = new StringBuilder("<w:p>");
        if (style != null) {
            sb.append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>");
        }
        boolean any = false;
        for (ReportDocument.Run run : runs) {
            String text = run.text();
            if (text == null || text.isEmpty()) {
                continue;
            }
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append("<w:r><w:br/></w:r>");
                }
                if (lines[i].isEmpty()) {
                    continue;
                }
                any = true;
                sb.append("<w:r>");
                if (run.bold()) {
                    sb.append("<w:rPr><w:b/></w:rPr>");
                }
                // xml:space keeps the spaces Word would otherwise trim away.
                sb.append("<w:t xml:space=\"preserve\">")
                        .append(OoxmlPackage.escape(lines[i]))
                        .append("</w:t></w:r>");
            }
        }
        if (!any) {
            // An empty paragraph is a blank line, which is what a gap in the
            // original layout should become.
            sb.append("<w:r><w:t/></w:r>");
        }
        return sb.append("</w:p>").toString();
    }

    /**
     * A table with borders, a shaded and bold heading row that repeats on each
     * page, and column widths taken from the content.
     */
    private static String table(ReportDocument.Table table) {
        int columns = Math.max(1, table.columnCount());
        int[] widths = columnWidths(table, columns);

        StringBuilder sb = new StringBuilder();
        sb.append("<w:tbl><w:tblPr>")
                .append("<w:tblStyle w:val=\"TableNormal\"/>")
                .append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>")
                .append("<w:tblBorders>")
                .append("<w:top w:val=\"single\" w:sz=\"6\" w:color=\"BFC9D1\"/>")
                .append("<w:left w:val=\"single\" w:sz=\"6\" w:color=\"BFC9D1\"/>")
                .append("<w:bottom w:val=\"single\" w:sz=\"6\" w:color=\"BFC9D1\"/>")
                .append("<w:right w:val=\"single\" w:sz=\"6\" w:color=\"BFC9D1\"/>")
                .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D8DEE4\"/>")
                .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D8DEE4\"/>")
                .append("</w:tblBorders>")
                .append("<w:tblLayout w:type=\"fixed\"/>")
                .append("</w:tblPr><w:tblGrid>");
        for (int width : widths) {
            sb.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        sb.append("</w:tblGrid>");

        if (!table.header().isEmpty()) {
            sb.append("<w:tr><w:trPr><w:tblHeader/></w:trPr>");
            for (int i = 0; i < columns; i++) {
                String value = i < table.header().size() ? table.header().get(i) : "";
                sb.append(cell(value, true, widths[i]));
            }
            sb.append("</w:tr>");
        }
        for (List<String> row : table.rows()) {
            sb.append("<w:tr>");
            for (int i = 0; i < columns; i++) {
                String value = i < row.size() ? row.get(i) : "";
                sb.append(cell(value, false, widths[i]));
            }
            sb.append("</w:tr>");
        }
        return sb.append("</w:tbl>").toString();
    }

    private static String cell(String value, boolean header, int width) {
        StringBuilder sb = new StringBuilder("<w:tc><w:tcPr>");
        sb.append("<w:tcW w:w=\"").append(width).append("\" w:type=\"dxa\"/>");
        if (header) {
            sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF4F3\"/>");
        }
        sb.append("</w:tcPr>");
        if (value == null || value.isBlank()) {
            // A blank cell still needs a paragraph to be a valid cell.
            sb.append("<w:p/>");
        } else {
            sb.append("<w:p><w:pPr><w:spacing w:after=\"20\"/></w:pPr>");
            String[] lines = value.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append("<w:r><w:br/></w:r>");
                }
                sb.append("<w:r>");
                if (header) {
                    sb.append("<w:rPr><w:b/></w:rPr>");
                }
                sb.append("<w:t xml:space=\"preserve\">")
                        .append(OoxmlPackage.escape(lines[i])).append("</w:t></w:r>");
            }
            sb.append("</w:p>");
        }
        return sb.append("</w:tc>").toString();
    }

    /**
     * Column widths in twentieths of a point.
     *
     * <p>Proportional to the longest value in each column, with a floor so a narrow
     * column stays readable and a ceiling so one long note cannot squeeze every
     * other column away.
     */
    private static int[] columnWidths(ReportDocument.Table table, int columns) {
        int available = PAGE_WIDTH_TWIPS - 2 * MARGIN_TWIPS;
        double[] weights = new double[columns];
        for (int i = 0; i < columns; i++) {
            int longest = i < table.header().size() ? table.header().get(i).length() : 0;
            for (List<String> row : table.rows()) {
                if (i < row.size()) {
                    longest = Math.max(longest, longestLine(row.get(i)));
                }
            }
            weights[i] = Math.max(4, Math.min(48, longest));
        }
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        int[] widths = new int[columns];
        int assigned = 0;
        for (int i = 0; i < columns; i++) {
            widths[i] = (int) Math.round(available * (weights[i] / total));
            assigned += widths[i];
        }
        // Give any rounding remainder to the widest column.
        if (assigned != available) {
            int widest = 0;
            for (int i = 1; i < columns; i++) {
                if (widths[i] > widths[widest]) {
                    widest = i;
                }
            }
            widths[widest] += available - assigned;
        }
        return widths;
    }

    private static int longestLine(String value) {
        int longest = 0;
        for (String line : value.split("\n")) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }
}
