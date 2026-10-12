package app.kinderjournal.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small text helpers used across the app. */
public final class Text {

    private Text() {
    }

    /** True when the value is null, empty or only whitespace. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Trims a value, mapping null/blank to the empty string. */
    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** Trims a value, mapping null/blank to {@code null} (useful before saving). */
    public static String nullIfBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /** Uppercases the first character (used for initials and headings). */
    public static String capitalise(String value) {
        String v = clean(value);
        if (v.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    /** Collapses runs of whitespace and truncates to {@code max} characters. */
    public static String summarise(String value, int max) {
        String v = clean(value).replaceAll("\\s+", " ");
        if (v.length() <= max) {
            return v;
        }
        int cut = v.lastIndexOf(' ', Math.max(1, max - 1));
        if (cut < max / 2) {
            cut = max - 1;
        }
        return v.substring(0, cut).trim() + "\u2026";
    }

    /** Up to two initials for an avatar, e.g. "Anna Maria Berg" -&gt; "AB". */
    public static String initials(String fullName) {
        String v = clean(fullName);
        if (v.isEmpty()) {
            return "?";
        }
        String[] parts = v.split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1 && !parts[parts.length - 1].isEmpty()) {
            sb.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return sb.toString();
    }

    /** A stable pseudo-random colour index for a name, so avatars keep their tint. */
    public static int colourIndex(String seed, int buckets) {
        if (buckets <= 0) {
            return 0;
        }
        int hash = 0;
        for (char c : clean(seed).toCharArray()) {
            hash = hash * 31 + c;
        }
        return Math.floorMod(hash, buckets);
    }

    /** Splits a comma/semicolon/newline separated list into trimmed items. */
    public static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        if (isBlank(value)) {
            return out;
        }
        for (String part : value.split("[,;\\n]")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    /** Joins non-blank parts with a separator, skipping empties. */
    public static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (isBlank(p)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(p.trim());
        }
        return sb.toString();
    }

    /** Escapes text for safe inclusion in generated HTML reports. */
    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                case '\n' -> sb.append("<br>");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * The inverse of {@link #escapeHtml}, for reading a generated report back.
     *
     * <p>Used when a printed report is turned into a Word or Excel file: the
     * renderer's HTML is parsed back into text, and the entities it wrote have to
     * become characters again or the teacher sees "&amp;amp;" in the document.
     */
    public static String unescapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", "\u00a0")
                // Ampersand last, so an escaped entity is not decoded twice.
                .replace("&amp;", "&");
    }

    /** Escapes text and converts blank lines into paragraph breaks. */
    public static String escapeHtmlParagraphs(String value) {
        if (isBlank(value)) {
            return "";
        }
        String[] paragraphs = value.replace("\r\n", "\n").split("\n\\s*\n");
        StringBuilder sb = new StringBuilder();
        for (String p : paragraphs) {
            if (p.isBlank()) {
                continue;
            }
            sb.append("<p>").append(escapeHtml(p.strip())).append("</p>");
        }
        return sb.toString();
    }

    /** Lowercase copy for case-insensitive comparisons, never null. */
    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /** Pluralises a noun based on the count, e.g. 1 child / 2 children. */
    public static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
