package app.kinderjournal.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one place that defines every choice list used by forms, filters and
 * printed reports.
 *
 * <p>Keeping them together means a category added here immediately appears in
 * the observation form, the filter dropdown, the search screen and the report
 * builder. Choices are stored in the database by their stable {@code id}, so
 * labels can be reworded later without breaking existing records.
 */
public final class Choices {

    private Choices() {
    }

    // ---------------------------------------------------------------- attendance

    /** Attendance status. */
    public enum AttendanceStatus {
        PRESENT("present", "Present", "P"),
        ABSENT("absent", "Absent", "A"),
        LATE("late", "Late", "L"),
        EXCUSED("excused", "Excused", "E");

        private final String id;
        private final String label;
        private final String shortCode;

        AttendanceStatus(String id, String label, String shortCode) {
            this.id = id;
            this.label = label;
            this.shortCode = shortCode;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        /** Single-letter code used in compact calendar cells. */
        public String shortCode() {
            return shortCode;
        }

        /** True when the child was actually in the group that day. */
        public boolean countedAsAttending() {
            return this == PRESENT || this == LATE;
        }

        public static AttendanceStatus fromId(String id) {
            for (AttendanceStatus s : values()) {
                if (s.id.equalsIgnoreCase(id)) {
                    return s;
                }
            }
            return PRESENT;
        }
    }

    // -------------------------------------------------------------- observations

    /** Developmental area an observation is filed under. */
    public enum ObservationCategory {
        PHYSICAL("physical", "Physical development", "#2e7d32"),
        LANGUAGE("language", "Language & communication", "#1565c0"),
        SOCIAL("social", "Social interaction", "#6a1b9a"),
        EMOTIONAL("emotional", "Emotional development", "#ad1457"),
        COGNITIVE("cognitive", "Cognitive development & learning", "#ef6c00"),
        PARTICIPATION("participation", "Participation in activities", "#00838f"),
        BEHAVIOUR("behaviour", "Behaviour & daily notes", "#5d4037"),
        ACHIEVEMENT("achievement", "Achievements", "#c62828"),
        SUPPORT("support", "Areas needing support", "#455a64"),
        GENERAL("general", "General observation", "#37474f");

        private final String id;
        private final String label;
        private final String colour;

        ObservationCategory(String id, String label, String colour) {
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

        /** Hex colour used for the category chip in lists and reports. */
        public String colour() {
            return colour;
        }

        public static ObservationCategory fromId(String id) {
            for (ObservationCategory c : values()) {
                if (c.id.equalsIgnoreCase(id)) {
                    return c;
                }
            }
            return GENERAL;
        }

        /** Categories offered in the "new observation" form (everything). */
        public static List<ObservationCategory> all() {
            return List.of(values());
        }
    }

    // ---------------------------------------------------------------- interviews

    /** Kind of interview being recorded. */
    public enum InterviewType {
        PARENT("parent", "Parent interview"),
        CHILD("child", "Child interview"),
        GUARDIAN("guardian", "Guardian / carer interview"),
        OTHER_PROFESSIONAL("professional", "Other professional"),
        PHONE("phone", "Telephone conversation"),
        OTHER("other", "Other conversation");

        private final String id;
        private final String label;

        InterviewType(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static InterviewType fromId(String id) {
            for (InterviewType t : values()) {
                if (t.id.equalsIgnoreCase(id)) {
                    return t;
                }
            }
            return OTHER;
        }
    }

    /** How an interview was conducted, when the teacher wants to note it. */
    public enum InterviewMode {
        IN_PERSON("in-person", "In person"),
        TELEPHONE("telephone", "Telephone"),
        VIDEO("video", "Video call"),
        WRITTEN("written", "Written / message");

        private final String id;
        private final String label;

        InterviewMode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static InterviewMode fromId(String id) {
            for (InterviewMode m : values()) {
                if (m.id.equalsIgnoreCase(id)) {
                    return m;
                }
            }
            return IN_PERSON;
        }
    }

    // -------------------------------------------------------------- lesson plans

    /** Lifecycle of a planned activity. */
    public enum PlanStatus {
        PLANNED("planned", "Planned"),
        COMPLETED("completed", "Completed"),
        POSTPONED("postponed", "Postponed"),
        CANCELLED("cancelled", "Cancelled");

        private final String id;
        private final String label;

        PlanStatus(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static PlanStatus fromId(String id) {
            for (PlanStatus s : values()) {
                if (s.id.equalsIgnoreCase(id)) {
                    return s;
                }
            }
            return PLANNED;
        }
    }

    /** Subject / developmental area a plan targets. */
    public enum PlanArea {
        LANGUAGE("language", "Language & literacy"),
        MATHS("maths", "Mathematics & logic"),
        SCIENCE("science", "Nature & science"),
        ART("art", "Art & creativity"),
        MUSIC("music", "Music & movement"),
        MOTOR("motor", "Motor skills & sport"),
        SOCIAL("social", "Social & emotional learning"),
        PLAY("play", "Free play"),
        OUTDOOR("outdoor", "Outdoor activity"),
        ROUTINE("routine", "Daily routine"),
        THEME("theme", "Theme / project work"),
        OTHER("other", "Other");

        private final String id;
        private final String label;

        PlanArea(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static PlanArea fromId(String id) {
            for (PlanArea a : values()) {
                if (a.id.equalsIgnoreCase(id)) {
                    return a;
                }
            }
            return OTHER;
        }
    }

    // ------------------------------------------------------------------- helpers

    /**
     * Builds an id-to-label map for the given enum, for use in combo boxes and
     * filter dropdowns.
     */
    public static <E extends Enum<E>> Map<String, String> labelMap(
            List<E> values,
            java.util.function.Function<E, String> idOf,
            java.util.function.Function<E, String> labelOf) {
        Map<String, String> map = new LinkedHashMap<>();
        for (E value : values) {
            map.put(idOf.apply(value), labelOf.apply(value));
        }
        return map;
    }

    /** All observation categories as an id-to-label map, in declaration order. */
    public static Map<String, String> observationLabelMap() {
        return labelMap(List.of(ObservationCategory.values()),
                ObservationCategory::id, ObservationCategory::label);
    }

    /** All attendance statuses as an id-to-label map. */
    public static Map<String, String> attendanceLabelMap() {
        return labelMap(List.of(AttendanceStatus.values()),
                AttendanceStatus::id, AttendanceStatus::label);
    }

    /** Human label for a stored category id, falling back to a tidied id. */
    public static String observationLabel(String id) {
        return observationLabelMap().getOrDefault(id, id == null ? "" : id);
    }

    /** Comma-separated list of every observation category label (for help text). */
    public static String observationLabelsJoined() {
        return List.of(ObservationCategory.values()).stream()
                .map(ObservationCategory::label)
                .collect(Collectors.joining(", "));
    }
}
