package app.kinderjournal.report;

import app.kinderjournal.i18n.Messages;

/**
 * The file formats a report can be saved in besides printing.
 *
 * <p>Both are produced by this program rather than by a document library, so the
 * wording here is what the teacher sees in the save dialog.
 */
public enum DocumentFormat {

    /** An editable Word document, .docx. */
    WORD("docx", "word"),

    /** A spreadsheet, .xlsx. */
    EXCEL("xlsx", "excel");

    private final String extension;
    private final String key;

    DocumentFormat(String extension, String key) {
        this.extension = extension;
        this.key = key;
    }

    /** The file extension, without a dot. */
    public String extension() {
        return extension;
    }

    /** The name of the format, in the teacher's language. */
    public String label() {
        return Messages.get("format." + key);
    }

    /** The filter description for a file chooser, in the teacher's language. */
    public String filterLabel() {
        return Messages.get("format." + key + ".filter");
    }

    /** The file-name filter pattern for a file chooser. */
    public String filterPattern() {
        return "*." + extension;
    }

    /**
     * The format that suits a report type by default.
     *
     * <p>Excel is offered only where the report always produces a table, because a
     * spreadsheet of prose is no use to anyone. Word is offered wherever the report
     * has something to say. Both rules are checked against the reports themselves by
     * the tests, so a layout change that removes a table cannot leave the screen
     * offering a format with nothing to put in it.
     */
    public static DocumentFormat[] forType(ReportType type) {
        return switch (type) {
            // Registers and tallies: one row per child, which is what a spreadsheet
            // is for.
            case ATTENDANCE_DAY, ATTENDANCE_SUMMARY, ATTENDANCE_MONTH ->
                    new DocumentFormat[]{EXCEL, WORD};
            // Written documents: notes, interviews and plans, which a teacher edits.
            case CHILD_PROFILE, CHILD_OBSERVATIONS, INTERVIEW_SUMMARY, DAILY_PLAN,
                 WEEKLY_PLAN -> new DocumentFormat[]{WORD};
            // A child's history is a dated list of entries rather than a table, so
            // it is a document; a period report has a register in it and suits both.
            case CHILD_HISTORY -> new DocumentFormat[]{WORD};
            case CLASS_PERIOD, SEARCH_RESULTS -> new DocumentFormat[]{WORD, EXCEL};
        };
    }

    /** True when saving this report type in this format is offered. */
    public static boolean suits(ReportType type, DocumentFormat format) {
        for (DocumentFormat candidate : forType(type)) {
            if (candidate == format) {
                return true;
            }
        }
        return false;
    }
}
