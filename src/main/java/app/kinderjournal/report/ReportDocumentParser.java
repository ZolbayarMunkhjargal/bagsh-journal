package app.kinderjournal.report;

import app.kinderjournal.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a rendered report's HTML back into a {@link ReportDocument}.
 *
 * <p>Only the markup the report renderer emits is handled: headings, paragraphs,
 * note blocks, entry blocks, lists, tables and the signature line. Anything else
 * has its tags stripped and its text kept, so an unexpected element degrades to a
 * plain sentence rather than losing the teacher's words.
 */
public final class ReportDocumentParser {

    private static final Pattern BODY = Pattern.compile("(?s)<body[^>]*>(.*)</body>");
    private static final Pattern TITLE = Pattern.compile("(?s)<h1[^>]*>(.*?)</h1>");
    private static final Pattern SUBTITLE =
            Pattern.compile("(?s)<div class=\"subtitle\"[^>]*>(.*?)</div>");

    /** The block-level elements, in the order they should be looked for. */
    private static final Pattern BLOCK = Pattern.compile(
            "(?s)<h1[^>]*>(.*?)</h1>"
                    + "|<h2[^>]*>(.*?)</h2>"
                    + "|<h3[^>]*>(.*?)</h3>"
                    + "|<table[^>]*>(.*?)</table>"
                    + "|<ul[^>]*>(.*?)</ul>"
                    + "|<div class=\"entry\"[^>]*>(.*?)</div>\\s*(?=<div class=\"entry\"|<h2|<table|<ul|<div class=\"signature\"|$)"
                    + "|<div class=\"note-block\"[^>]*>(.*?)</div>"
                    + "|<div class=\"summary-box\"[^>]*>(.*?)</div>"
                    + "|<div class=\"signature\"[^>]*>(.*?)</div>"
                    + "|<p[^>]*>(.*?)</p>");

    private static final Pattern TABLE_ROW = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_HEADER = Pattern.compile("(?s)<thead[^>]*>(.*?)</thead>");
    private static final Pattern CELL = Pattern.compile("(?s)<t[hd][^>]*>(.*?)</t[hd]>");
    private static final Pattern LIST_ITEM = Pattern.compile("(?s)<li[^>]*>(.*?)</li>");
    private static final Pattern STRONG = Pattern.compile("(?s)<strong[^>]*>(.*?)</strong>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern IMAGE = Pattern.compile("(?s)<img[^>]*>");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern ENTRY_PART =
            Pattern.compile("(?s)<div[^>]*>(.*?)</div>");
    private static final Pattern SUMMARY_VALUE =
            Pattern.compile("(?s)<div class=\"value\"[^>]*>(.*?)</div>");
    private static final Pattern SUMMARY_LABEL =
            Pattern.compile("(?s)<div class=\"label\"[^>]*>(.*?)</div>");

    private ReportDocumentParser() {
    }

    /** Parses the HTML of a rendered report. */
    public static ReportDocument parse(String html) {
        String body = html == null ? "" : html;
        Matcher bodyMatch = BODY.matcher(body);
        if (bodyMatch.find()) {
            body = bodyMatch.group(1);
        }

        String title = firstGroup(TITLE, body);
        String subtitle = firstGroup(SUBTITLE, body);

        List<ReportDocument.Block> blocks = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(body);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // The h1 is the title and the subtitle follows it; both are already
                // captured, so repeating them in the body would print them twice.
                continue;
            } else if (matcher.group(2) != null) {                add(blocks, new ReportDocument.Heading(2, text(matcher.group(2))));
            } else if (matcher.group(3) != null) {
                add(blocks, new ReportDocument.Heading(3, text(matcher.group(3))));
            } else if (matcher.group(4) != null) {
                ReportDocument.Table table = table(matcher.group(4));
                if (table != null) {
                    blocks.add(table);
                }
            } else if (matcher.group(5) != null) {
                List<String> items = new ArrayList<>();
                Matcher item = LIST_ITEM.matcher(matcher.group(5));
                while (item.find()) {
                    String value = text(item.group(1));
                    if (!value.isBlank()) {
                        items.add(value);
                    }
                }
                if (!items.isEmpty()) {
                    blocks.add(new ReportDocument.Bullets(items));
                }
            } else if (matcher.group(6) != null) {
                add(blocks, entry(matcher.group(6)));
            } else if (matcher.group(7) != null) {
                add(blocks, new ReportDocument.Paragraph(
                        List.of(new ReportDocument.Run(text(matcher.group(7))))));
            } else if (matcher.group(8) != null) {
                add(blocks, summaryBox(matcher.group(8)));
            } else if (matcher.group(9) != null) {
                add(blocks, new ReportDocument.Paragraph(
                        List.of(new ReportDocument.Run(text(matcher.group(9))))));
            } else if (matcher.group(10) != null) {
                add(blocks, new ReportDocument.Paragraph(runs(matcher.group(10))));
            }
        }

        if (title.isBlank()) {
            title = blocks.stream()
                    .filter(ReportDocument.Heading.class::isInstance)
                    .map(ReportDocument.Heading.class::cast)
                    .map(ReportDocument.Heading::text)
                    .findFirst()
                    .orElse("");
        }
        return new ReportDocument(title, subtitle, blocks);
    }

    // ------------------------------------------------------------------ pieces

    /**
     * An entry block: a dated line, a bold headline and a note beneath it.
     *
     * <p>Written as the date and kind, then the headline, then the note, so the
     * result reads as a paragraph rather than as a label and a stray value. The
     * parts are kept as runs rather than as plain text, because the headline is
     * bold in the report and should still be bold in the Word document.
     */
    private static ReportDocument.Block entry(String inner) {
        List<List<ReportDocument.Run>> parts = new ArrayList<>();
        Matcher piece = ENTRY_PART.matcher(inner);
        while (piece.find()) {
            List<ReportDocument.Run> runs = runs(piece.group(1));
            if (!runs.isEmpty()) {
                parts.add(runs);
            }
        }
        if (parts.isEmpty()) {
            return new ReportDocument.Paragraph(runs(inner));
        }

        List<ReportDocument.Run> all = new ArrayList<>();
        if (parts.size() == 1) {
            all.addAll(parts.get(0));
            return new ReportDocument.Paragraph(all);
        }
        // The date and kind lead, then the substance of the entry.
        List<ReportDocument.Run> lead = parts.get(0);
        all.add(new ReportDocument.Run(join(lead) + " \u2014 ", true));
        for (int i = 1; i < parts.size(); i++) {
            if (i > 1) {
                all.add(new ReportDocument.Run("\n"));
            }
            all.addAll(parts.get(i));
        }
        return new ReportDocument.Paragraph(all);
    }

    /** The plain text of a run list. */
    private static String join(List<ReportDocument.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (ReportDocument.Run run : runs) {
            sb.append(run.text());
        }
        return sb.toString().trim();
    }

    /** A summary box is a big number with a caption; kept together on one line. */
    private static ReportDocument.Block summaryBox(String inner) {
        String value = firstGroup(SUMMARY_VALUE, inner);
        String label = firstGroup(SUMMARY_LABEL, inner);
        String combined = (label + ": " + value).trim();
        if (value.isBlank() && label.isBlank()) {
            combined = text(inner);
        }
        return new ReportDocument.Paragraph(
                List.of(new ReportDocument.Run(combined.replaceAll("^:\\s*", ""))));
    }

    private static ReportDocument.Table table(String inner) {
        List<String> header = new ArrayList<>();
        Matcher head = TABLE_HEADER.matcher(inner);
        String headerHtml = head.find() ? head.group(1) : "";
        if (!headerHtml.isBlank()) {
            Matcher row = TABLE_ROW.matcher(headerHtml);
            if (row.find()) {
                header.addAll(cells(row.group(1)));
            }
        }

        List<List<String>> rows = new ArrayList<>();
        Matcher row = TABLE_ROW.matcher(inner);
        while (row.find()) {
            String rowHtml = row.group(1);
            // Skip the heading row when it was inside thead and already taken.
            if (!headerHtml.isBlank() && headerHtml.contains(rowHtml)) {
                continue;
            }
            List<String> values = cells(rowHtml);
            if (!values.isEmpty() && values.stream().anyMatch(value -> !value.isBlank())) {
                rows.add(values);
            }
        }

        if (header.isEmpty() && rows.isEmpty()) {
            return null;
        }
        if (header.isEmpty() && !rows.isEmpty()) {
            // A table with no heading row: the first body row usually is one.
            header = rows.remove(0);
        }
        return new ReportDocument.Table(header, rows);
    }

    private static List<String> cells(String rowHtml) {
        List<String> values = new ArrayList<>();
        Matcher cell = CELL.matcher(rowHtml);
        while (cell.find()) {
            values.add(text(cell.group(1)));
        }
        return values;
    }

    // -------------------------------------------------------------------- text

    private static void add(List<ReportDocument.Block> blocks, ReportDocument.Block block) {
        if (block instanceof ReportDocument.Paragraph paragraph && paragraph.isBlank()) {
            return;
        }
        if (block instanceof ReportDocument.Heading heading && heading.text().isBlank()) {
            return;
        }
        blocks.add(block);
    }

    /** One plain string, tags removed and entities decoded. */
    private static String text(String html) {
        if (html == null) {
            return "";
        }
        String roughly = BREAK.matcher(html).replaceAll("\n");
        // Images carry no text, and their data URIs would otherwise leak in.
        roughly = IMAGE.matcher(roughly).replaceAll("");
        roughly = TAG.matcher(roughly).replaceAll("");
        return tidy(Text.unescapeHtml(roughly));
    }

    /**
     * The runs of a paragraph, keeping bold text bold.
     *
     * <p>Anything inside a strong element becomes a bold run; everything else is
     * plain. A line break becomes its own run, so a note written over several lines
     * keeps its shape in the Word document.
     */
    private static List<ReportDocument.Run> runs(String html) {
        List<ReportDocument.Run> result = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return result;
        }
        String text = BREAK.matcher(html).replaceAll("\n");
        Matcher bold = STRONG.matcher(text);
        int cursor = 0;
        while (bold.find()) {
            appendRun(result, text.substring(cursor, bold.start()), false);
            appendRun(result, bold.group(1), true);
            cursor = bold.end();
        }
        appendRun(result, text.substring(cursor), false);
        return result;
    }

    private static void appendRun(List<ReportDocument.Run> result, String html, boolean isBold) {
        String value = html == null ? "" : html;
        value = IMAGE.matcher(value).replaceAll("");
        value = TAG.matcher(value).replaceAll("");
        value = tidy(Text.unescapeHtml(value));
        if (!value.isBlank()) {
            result.add(new ReportDocument.Run(value, isBold));
        }
    }

    /**
     * Collapses the whitespace HTML uses for layout, and keeps deliberate breaks.
     *
     * <p>The renderer wraps its markup over many source lines for readability, so
     * those have to be joined with a space; a line break that was actually asked
     * for arrives as a newline and is kept.
     */
    private static String tidy(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = cleaned.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.replaceAll("[ \t]+", " ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString().trim();
    }

    private static String firstGroup(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html == null ? "" : html);
        return matcher.find() ? text(matcher.group(1)) : "";
    }
}
