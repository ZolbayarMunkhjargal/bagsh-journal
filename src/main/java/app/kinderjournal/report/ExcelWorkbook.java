package app.kinderjournal.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves a report as an Excel workbook.
 *
 * <p>Written as SpreadsheetML by hand - see {@link OoxmlPackage} for why. Each
 * table in the report becomes its own sheet, named after the heading it sat under,
 * with the report's title and context above it. Numbers are written as numbers and
 * percentages as percentages, so a teacher can add a column up or sort by it
 * without Excel treating the values as text.
 */
public final class ExcelWorkbook {

    /** Excel refuses anything longer, and rejects a few characters outright. */
    private static final int MAX_SHEET_NAME = 31;
    private static final int MAX_COLUMN_WIDTH = 60;
    private static final int MIN_COLUMN_WIDTH = 9;

    private ExcelWorkbook() {
    }

    /** Writes {@code document} to {@code file}, creating folders as needed. */
    public static void write(ReportDocument document, Path file) throws IOException {
        List<ReportDocument.Table> tables = document.tables();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException(
                    app.kinderjournal.i18n.Messages.get("format.onlyTables"));
        }

        List<String> names = sheetNames(document, tables);
        try (OoxmlPackage pkg = new OoxmlPackage(file)) {
            pkg.part("[Content_Types].xml", contentTypes(tables.size()), true);
            pkg.part("_rels/.rels", packageRels(), false);
            pkg.part("xl/workbook.xml", workbook(names), false);
            pkg.part("xl/_rels/workbook.xml.rels", workbookRels(tables.size()), false);
            pkg.part("xl/styles.xml", styles(), false);
            for (int i = 0; i < tables.size(); i++) {
                pkg.part("xl/worksheets/sheet" + (i + 1) + ".xml",
                        sheet(document, tables.get(i)), false);
            }
        }
    }

    // ------------------------------------------------------------------- parts

    private static String contentTypes(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                """);
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("  <Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-")
                    .append("officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        return sb.append("</Types>").toString();
    }

    private static String packageRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""";
    }

    private static String workbook(List<String> sheetNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                """);
        for (int i = 0; i < sheetNames.size(); i++) {
            sb.append("    <sheet name=\"").append(OoxmlPackage.escape(sheetNames.get(i)))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>\n");
        }
        return sb.append("  </sheets>\n</workbook>").toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                """);
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("  <Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/")
                    .append("relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>\n");
        }
        sb.append("  <Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/")
                .append("relationships/styles\" Target=\"styles.xml\"/>\n");
        return sb.append("</Relationships>").toString();
    }

    /**
     * The cell formats used: index 0 is plain, 1 bold, 2 large bold for the title
     * and 3 the percentage format. Excel refers to them by index, so the order
     * matters and {@code cellXfs} has to list them all.
     */
    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="1"><numFmt numFmtId="164" formatCode="0%"/></numFmts>
                  <fonts count="3">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="14"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FFEEF4F3"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="2">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                    <border>
                      <left style="thin"><color rgb="FFBFC9D1"/></left>
                      <right style="thin"><color rgb="FFBFC9D1"/></right>
                      <top style="thin"><color rgb="FFBFC9D1"/></top>
                      <bottom style="thin"><color rgb="FFBFC9D1"/></bottom>
                      <diagonal/>
                    </border>
                  </borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="5">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
                      <alignment vertical="center" wrapText="1"/></xf>
                    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
                      <alignment vertical="top" wrapText="1"/></xf>
                  </cellXfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>""";
    }

    // ------------------------------------------------------------------ sheets

    private static String sheet(ReportDocument document, ReportDocument.Table table) {
        int columns = Math.max(1, table.columnCount());
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                """);

        sb.append("<cols>");
        int[] widths = columnWidths(table, columns);
        for (int i = 0; i < columns; i++) {
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");

        int row = 1;
        if (!document.title().isBlank()) {
            sb.append(textRow(row++, 0, document.title(), 2));
        }
        if (!document.subtitle().isBlank()) {
            sb.append(textRow(row++, 0, document.subtitle(), 0));
        }
        // A blank row between the heading and the data, so the table stands apart.
        row++;
        if (!table.header().isEmpty()) {
            sb.append("<row r=\"").append(row++).append("\">");
            for (int i = 0; i < columns; i++) {
                String value = i < table.header().size() ? table.header().get(i) : "";
                sb.append(cell(i, row - 1, value, true));
            }
            sb.append("</row>");
        }
        for (List<String> values : table.rows()) {
            sb.append("<row r=\"").append(row).append("\">");
            for (int i = 0; i < columns; i++) {
                String value = i < values.size() ? values.get(i) : "";
                sb.append(cell(i, row, value, false));
            }
            sb.append("</row>");
            row++;
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String textRow(int rowNumber, int column, String value, int style) {
        return "<row r=\"" + rowNumber + "\">" + cell(column, rowNumber, value, style != 0)
                + "</row>";
    }

    /**
     * One cell.
     *
     * <p>A value that is plainly a number becomes a numeric cell so Excel can total
     * it, and "91%" keeps its percent sign as a format rather than as text.
     */
    private static String cell(int column, int row, String value, boolean header) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String reference = columnName(column) + row;
        if (header) {
            return "<c r=\"" + reference + "\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                    + OoxmlPackage.escape(value) + "</t></is></c>";
        }
        String trimmed = value.trim();
        boolean percent = trimmed.endsWith("%");
        String numeric = percent ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (isNumeric(numeric)) {
            // Percentages are stored as the fraction Excel expects.
            String stored = percent ? String.valueOf(parse(numeric) / 100.0) : numeric;
            String style = percent ? " s=\"3\"" : " s=\"4\"";
            return "<c r=\"" + reference + "\"" + style + "><v>" + stored + "</v></c>";
        }
        return "<c r=\"" + reference + "\" s=\"4\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + OoxmlPackage.escape(trimmed) + "</t></is></c>";
    }

    /**
     * True for a value that is certainly a number.
     *
     * <p>Deliberately strict: "07434 821850" is a phone number and "12 Oct 2026" is
     * a date, and neither should be turned into a number or Excel will mangle it.
     */
    private static boolean isNumeric(String value) {
        if (value.isEmpty() || value.length() > 18) {
            return false;
        }
        boolean digitSeen = false;
        boolean dotSeen = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digitSeen = true;
            } else if (c == '.' && !dotSeen) {
                dotSeen = true;
            } else if (c == '-' && i == 0) {
                continue;
            } else {
                return false;
            }
        }
        return digitSeen;
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int value = index;
        while (value >= 0) {
            sb.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        }
        return sb.toString();
    }

    private static int[] columnWidths(ReportDocument.Table table, int columns) {
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            int longest = i < table.header().size() ? table.header().get(i).length() : 0;
            for (List<String> row : table.rows()) {
                if (i < row.size()) {
                    longest = Math.max(longest, row.get(i).length());
                }
            }
            widths[i] = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, longest + 3));
        }
        return widths;
    }

    // ------------------------------------------------------------- sheet names

    /**
     * Sheet names, taken from the nearest heading above each table.
     *
     * <p>Made unique because Excel refuses a workbook with two sheets of the same
     * name, and sanitised because it also refuses a name containing : \ / ? * [ ].
     */
    private static List<String> sheetNames(ReportDocument document, List<ReportDocument.Table> tables) {
        List<String> raw = new ArrayList<>();
        String pending = "";
        int taken = 0;
        for (ReportDocument.Block block : document.blocks()) {
            if (block instanceof ReportDocument.Heading heading) {
                pending = heading.text();
            } else if (block instanceof ReportDocument.Table) {
                raw.add(pending.isBlank() ? "Sheet " + (taken + 1) : pending);
                pending = "";
                taken++;
            }
        }
        while (raw.size() < tables.size()) {
            raw.add("Sheet " + (raw.size() + 1));
        }

        List<String> names = new ArrayList<>();
        for (String candidate : raw) {
            String cleaned = candidate.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
            if (cleaned.isEmpty()) {
                cleaned = "Sheet";
            }
            if (cleaned.length() > MAX_SHEET_NAME) {
                cleaned = cleaned.substring(0, MAX_SHEET_NAME).trim();
            }
            String unique = cleaned;
            int suffix = 2;
            while (names.contains(unique)) {
                String tail = " (" + suffix + ")";
                int keep = Math.min(cleaned.length(), MAX_SHEET_NAME - tail.length());
                unique = cleaned.substring(0, Math.max(1, keep)) + tail;
                suffix++;
            }
            names.add(unique);
        }
        return names;
    }
}
