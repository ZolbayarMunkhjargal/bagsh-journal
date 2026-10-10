package app.kinderjournal.model;

import app.kinderjournal.i18n.Messages;

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
 * labels can be reworded - or translated - without breaking existing records.
 *
 * <p>Each constant carries a message key rather than a fixed label, and
 * {@link #label()} looks that key up in the currently selected language. Because
 * every screen, filter and report prints these through {@code label()}, changing
 * language translates all of them at once.
 */
public final class Choices {

    private Choices() {
    }

    /** Anything that can be shown to the teacher under a translated label. */
    public interface Labelled {

        /** Stable identifier stored in the database. */
        String id();

        /** Message key for the human-readable label. */
        String key();

        /** The label in the language currently selected. */
        default String label() {
            return Messages.get(key());
        }
    }

    // ---------------------------------------------------------------- attendance

    /** Attendance status. */
    public enum AttendanceStatus implements Labelled {
        PRESENT("present", "P"),
        ABSENT("absent", "A"),
        LATE("late", "L"),
        EXCUSED("excused", "E");

        private final String id;
        private final String shortCode;

        AttendanceStatus(String id, String shortCode) {
            this.id = id;
            this.shortCode = shortCode;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.attendance." + id;
        }

        /**
         * Single-letter code used in compact calendar cells and the monthly grid.
         * Translated, because some languages use a different initial.
         */
        public String shortCode() {
            return Messages.get("choice.attendance." + id + ".short", shortCode);
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
    public enum ObservationCategory implements Labelled {
        PHYSICAL("physical", "#2e7d32"),
        LANGUAGE("language", "#1565c0"),
        SOCIAL("social", "#6a1b9a"),
        EMOTIONAL("emotional", "#ad1457"),
        COGNITIVE("cognitive", "#ef6c00"),
        PARTICIPATION("participation", "#00838f"),
        BEHAVIOUR("behaviour", "#5d4037"),
        ACHIEVEMENT("achievement", "#c62828"),
        SUPPORT("support", "#455a64"),
        GENERAL("general", "#37474f");

        private final String id;
        private final String colour;

        ObservationCategory(String id, String colour) {
            this.id = id;
            this.colour = colour;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.observation." + id;
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
    public enum InterviewType implements Labelled {
        PARENT("parent"),
        CHILD("child"),
        GUARDIAN("guardian"),
        OTHER_PROFESSIONAL("professional"),
        PHONE("phone"),
        OTHER("other");

        private final String id;

        InterviewType(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.interview." + id;
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
    public enum InterviewMode implements Labelled {
        IN_PERSON("in-person"),
        TELEPHONE("telephone"),
        VIDEO("video"),
        WRITTEN("written");

        private final String id;

        InterviewMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.mode." + id;
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
    public enum PlanStatus implements Labelled {
        PLANNED("planned"),
        COMPLETED("completed"),
        POSTPONED("postponed"),
        CANCELLED("cancelled");

        private final String id;

        PlanStatus(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.planStatus." + id;
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
    public enum PlanArea implements Labelled {
        LANGUAGE("language"),
        MATHS("maths"),
        SCIENCE("science"),
        ART("art"),
        MUSIC("music"),
        MOTOR("motor"),
        SOCIAL("social"),
        PLAY("play"),
        OUTDOOR("outdoor"),
        ROUTINE("routine"),
        THEME("theme"),
        OTHER("other");

        private final String id;

        PlanArea(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String key() {
            return "choice.planArea." + id;
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
     * Builds an id-to-label map for the given values, in declaration order.
     *
     * <p>The labels are read when this is called, so a screen rebuilds its
     * dropdowns after a language change rather than keeping the old text.
     */
    public static <E extends Labelled> Map<String, String> labelMap(List<E> values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (E value : values) {
            map.put(value.id(), value.label());
        }
        return map;
    }

    /** All observation categories as an id-to-label map, in declaration order. */
    public static Map<String, String> observationLabelMap() {
        return labelMap(List.of(ObservationCategory.values()));
    }

    /** All attendance statuses as an id-to-label map. */
    public static Map<String, String> attendanceLabelMap() {
        return labelMap(List.of(AttendanceStatus.values()));
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
