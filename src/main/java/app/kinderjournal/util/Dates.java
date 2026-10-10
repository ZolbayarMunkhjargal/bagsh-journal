package app.kinderjournal.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Date helpers shared by the UI and the persistence layer.
 *
 * <p>Dates are always stored in the database as ISO {@code yyyy-MM-dd} text so
 * that string comparison and range queries behave correctly, and are formatted
 * for people only when they reach the screen or a printed page.
 */
public final class Dates {

    /** How a date is stored in the database. */
    public static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /** How a date is shown to the teacher. */
    public static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Long form, used on report headings. */
    public static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /** Day + month, used in lists where the year is obvious from context. */
    public static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private Dates() {
    }

    public static String toIso(LocalDate date) {
        return date == null ? null : ISO.format(date);
    }

    public static LocalDate fromIso(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), ISO);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Formats an ISO date for display, returning an empty string for anything unusable. */
    public static String display(String iso) {
        LocalDate date = fromIso(iso);
        return date == null ? (iso == null ? "" : iso) : HUMAN.format(date);
    }

    /** Formats an ISO date in long form, e.g. for a report header. */
    public static String displayLong(String iso) {
        LocalDate date = fromIso(iso);
        return date == null ? (iso == null ? "" : iso) : LONG.format(date);
    }

    /** Human wording for "how long ago", e.g. "3 days ago" / "today". */
    public static String relative(String iso) {
        LocalDate date = fromIso(iso);
        if (date == null) {
            return "";
        }
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (days == 0) {
            return "today";
        }
        if (days == 1) {
            return "yesterday";
        }
        if (days == -1) {
            return "tomorrow";
        }
        if (days > 1 && days < 30) {
            return days + " days ago";
        }
        if (days < -1 && days > -30) {
            return "in " + (-days) + " days";
        }
        return display(iso);
    }

    /** Inclusive start of the week (Monday) containing {@code date}. */
    public static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() + 6) % 7);
    }

    /** Age in whole years, or -1 when the birth date is unknown/in the future. */
    public static int ageInYears(LocalDate birth, LocalDate on) {
        if (birth == null || on == null || birth.isAfter(on)) {
            return -1;
        }
        return (int) ChronoUnit.YEARS.between(birth, on);
    }

    /** A readable age such as "4 years 3 months", or "" when unknown. */
    public static String ageText(LocalDate birth, LocalDate on) {
        if (birth == null || on == null || birth.isAfter(on)) {
            return "";
        }
        long months = ChronoUnit.MONTHS.between(birth, on);
        long years = months / 12;
        long rem = months % 12;
        if (years <= 0) {
            return rem + (rem == 1 ? " month" : " months");
        }
        if (rem == 0) {
            return years + (years == 1 ? " year" : " years");
        }
        return years + (years == 1 ? " year " : " years ") + rem + (rem == 1 ? " month" : " months");
    }

    /** Parses whatever a teacher might type into a date field. */
    public static LocalDate parseLoose(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        LocalDate iso = fromIso(t);
        if (iso != null) {
            return iso;
        }
        String[] patterns = {"d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "M/d/yyyy", "d MMM yyyy", "d MMMM yyyy"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(t, DateTimeFormatter.ofPattern(p, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }
}
