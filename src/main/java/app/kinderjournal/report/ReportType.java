package app.kinderjournal.report;

/**
 * The document types the report builder can produce.
 *
 * <p>The constructor arguments are (label, description, defaultLandscape, needsChild).
 * A type with {@code needsChild} true is about one child, so the report screen asks for
 * a child before it will generate anything; the others cover a whole class.
 */
public enum ReportType {

    ATTENDANCE_DAY("Daily attendance sheet",
            "Every child in the class with their mark for one day, ready to sign and file.",
            false, false),
    ATTENDANCE_SUMMARY("Attendance summary",
            "Attendance totals per child for a date range, with class totals.", false, false),
    ATTENDANCE_MONTH("Monthly attendance grid",
            "A day-by-day grid of marks for the whole month.", true, false),
    CHILD_PROFILE("Child profile sheet",
            "The child's details, contacts and notes, for the class file.", false, true),
    CHILD_OBSERVATIONS("Observation report",
            "The child's observations for a period, grouped by developmental area.", false, true),
    CHILD_HISTORY("Child history report",
            "Everything recorded about one child in a period, in date order.", false, true),
    INTERVIEW_SUMMARY("Interview summary",
            "Parent and child interviews, or one child's, with conclusions and follow-ups.",
            false, false),
    DAILY_PLAN("Daily activity plan",
            "What is planned for one day, with objectives, sequence and materials.", false, false),
    WEEKLY_PLAN("Weekly activity plan",
            "The week at a glance, one column per day.", true, false),
    CLASS_PERIOD("Class report for a period",
            "Attendance, observations and interviews for a whole class over a date range.",
            false, false),
    SEARCH_RESULTS("Search results",
            "The records matching a search, listed with their dates and content.", false, false);

    private final String label;
    private final String description;
    private final boolean defaultLandscape;
    private final boolean needsChild;

    ReportType(String label, String description, boolean defaultLandscape, boolean needsChild) {
        this.label = label;
        this.description = description;
        this.defaultLandscape = defaultLandscape;
        this.needsChild = needsChild;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
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
