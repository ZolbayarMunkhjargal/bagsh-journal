package app.kinderjournal.model;

import app.kinderjournal.util.Text;

/**
 * One row in a child's chronological history.
 *
 * <p>The history screen merges attendance, observations, interviews, plan
 * involvement and photos into a single list, so each entry carries just enough
 * detail to render a summary line plus a pointer back to the full record.
 */
public record HistoryEntry(
        String date,
        Kind kind,
        long recordId,
        String title,
        String summary,
        String detail,
        String badgeColour,
        int photoCount) {

    /** The sort of record an entry came from. */
    public enum Kind {
        ATTENDANCE("attendance", "Attendance", "#00695c"),
        OBSERVATION("observation", "Observation", "#1565c0"),
        INTERVIEW("interview", "Interview", "#6a1b9a"),
        PLAN("plan", "Activity plan", "#ef6c00"),
        NOTE("note", "Profile note", "#455a64");

        private final String id;
        private final String label;
        private final String colour;

        Kind(String id, String label, String colour) {
            this.id = id;
            this.label = label;
            this.colour = colour;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String colour() {
            return colour;
        }

        public static Kind fromId(String id) {
            for (Kind k : values()) {
                if (k.id.equalsIgnoreCase(id)) {
                    return k;
                }
            }
            return NOTE;
        }
    }

    public static HistoryEntry attendance(String date, long id, String title, String summary) {
        return new HistoryEntry(date, Kind.ATTENDANCE, id, title, summary, "", Kind.ATTENDANCE.colour(), 0);
    }

    public static HistoryEntry observation(String date, long id, String title, String summary,
                                           String detail, String colour, int photos) {
        return new HistoryEntry(date, Kind.OBSERVATION, id, Text.clean(title), summary, detail,
                Text.isBlank(colour) ? Kind.OBSERVATION.colour() : colour, photos);
    }

    public static HistoryEntry interview(String date, long id, String title, String summary,
                                         String detail, int photos) {
        return new HistoryEntry(date, Kind.INTERVIEW, id, Text.clean(title), summary, detail,
                Kind.INTERVIEW.colour(), photos);
    }

    public static HistoryEntry plan(String date, long id, String title, String summary, String detail) {
        return new HistoryEntry(date, Kind.PLAN, id, Text.clean(title), summary, detail,
                Kind.PLAN.colour(), 0);
    }

    public static HistoryEntry note(String date, long id, String title, String summary) {
        return new HistoryEntry(date, Kind.NOTE, id, Text.clean(title), summary, "", Kind.NOTE.colour(), 0);
    }

    /** True when there is longer text worth showing in the detail pane. */
    public boolean hasDetail() {
        return !Text.isBlank(detail);
    }
}
