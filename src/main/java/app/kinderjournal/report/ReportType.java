package app.kinderjournal.report;

import app.kinderjournal.i18n.Messages;

/**
 * The document types the report builder can produce.
 *
 * <p>The constructor arguments are (key, defaultLandscape, needsChild). The name
 * and the one-line description are looked up per language through
 * {@link Messages}, so a printed report is titled in the teacher's language.
 *
 * <p>A type with {@code needsChild} true is about one child, so the report screen
 * asks for a child before it will generate anything; the others cover a whole
 * class.
 */
public enum ReportType {

    ATTENDANCE_DAY("ATTENDANCE_DAY", false, false),
    ATTENDANCE_SUMMARY("ATTENDANCE_SUMMARY", false, false),
    ATTENDANCE_MONTH("ATTENDANCE_MONTH", true, false),
    CHILD_PROFILE("CHILD_PROFILE", false, true),
    CHILD_OBSERVATIONS("CHILD_OBSERVATIONS", false, true),
    CHILD_HISTORY("CHILD_HISTORY", false, true),
    INTERVIEW_SUMMARY("INTERVIEW_SUMMARY", false, false),
    DAILY_PLAN("DAILY_PLAN", false, false),
    WEEKLY_PLAN("WEEKLY_PLAN", true, false),
    CLASS_PERIOD("CLASS_PERIOD", false, false),
    SEARCH_RESULTS("SEARCH_RESULTS", false, false);

    private final String key;
    private final boolean defaultLandscape;
    private final boolean needsChild;

    ReportType(String key, boolean defaultLandscape, boolean needsChild) {
        this.key = key;
        this.defaultLandscape = defaultLandscape;
        this.needsChild = needsChild;
    }

    /** The stable name of this report type. */
    public String key() {
        return key;
    }

    /** The document's title in the selected language. */
    public String label() {
        return Messages.get("report." + key);
    }

    /** The one-line explanation shown in the report picker. */
    public String description() {
        return Messages.get("report." + key + ".desc");
    }

    /** True when the document is wider than it is tall by default. */
    public boolean defaultLandscape() {
        return defaultLandscape;
    }

    /** True when the report is about one child, so a child must be chosen. */
    public boolean needsChild() {
        return needsChild;
    }

    /** True when the report is about a class rather than a single child. */
    public boolean needsGroup() {
        return !needsChild && this != SEARCH_RESULTS;
    }

    public static ReportType fromId(String id) {
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return ATTENDANCE_DAY;
    }
}
