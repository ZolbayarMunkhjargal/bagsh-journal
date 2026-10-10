package app.kinderjournal.util;

import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Date helpers shared by the UI and the persistence layer.
 *
 * <p>Dates are always stored in the database as ISO {@code yyyy-MM-dd} text so
 * that string comparison and range queries behave correctly, and are formatted
 * for people only when they reach the screen or a printed page.
 *
 * <p>Display formats follow the selected language, so month and weekday names are
 * translated along with the rest of the interface. The ISO form never changes.
 */
public final class Dates {

    /** How a date is stored in the database. Never translated. */
    public static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private Dates() {
    }

    // ------------------------------------------------------- Mongolian calendar
    //
    // The JDK ships no Mongolian locale data, so a DateTimeFormatter with a
    // Mongolian locale falls back to English month and weekday names. Rather than
    // accept English words inside an otherwise Mongolian document, the names are
    // held here. Mongolian month names are simply the number plus "сар", which is
    // also how they are written in everyday use.

    private static final String[] MN_WEEKDAYS = {
            "", "\u0414\u0430\u0432\u0430\u0430", "\u041c\u044f\u0433\u043c\u0430\u0440",
            "\u041b\u0445\u0430\u0433\u0432\u0430", "\u041f\u04af\u0440\u044d\u0432",
            "\u0411\u0430\u0430\u0441\u0430\u043d", "\u0411\u044f\u043c\u0431\u0430", "\u041d\u044f\u043c"};

    private static final String[] MN_WEEKDAYS_SHORT = {
            "", "\u0414\u0430\u0432", "\u041c\u044f\u0433", "\u041b\u0445\u0430", "\u041f\u04af\u0440",
            "\u0411\u0430\u0430", "\u0411\u044f\u043c", "\u041d\u044f\u043c"};

    /** The month's name in the selected language, without the year. */
    public static String monthNameOnly(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> date.getMonthValue() + " \u0441\u0430\u0440";
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("LLLL", Locale.ENGLISH));
        };
    }

    /** A short month name for compact labels such as a calendar cell. */
    public static String monthShort(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> date.getMonthValue() + " \u0441\u0430\u0440";
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH));
        };
    }

    /** The weekday's full name in the selected language. */
    public static String weekdayName(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> MN_WEEKDAYS[date.getDayOfWeek().getValue()];
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH));
        };
    }

    /** The weekday's short name in the selected language. */
    public static String weekdayNameShort(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> MN_WEEKDAYS_SHORT[date.getDayOfWeek().getValue()];
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH));
        };
    }

    // ------------------------------------------------------------------ display

    /**
     * "2 Mar 2026" in English, "2026 оны 3 сарын 2" in Mongolian.
     *
     * <p>Each language uses the pattern its readers expect rather than a single
     * reordered format, which is why the patterns differ rather than just the
     * locale.
     */
    public static String display(String iso) {
        LocalDate date = fromIso(iso);
        if (date == null) {
            return iso == null ? "" : iso;
        }
        return display(date);
    }

    public static String display(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> date.getYear() + " \u043e\u043d\u044b "
                    + date.getMonthValue() + " \u0441\u0430\u0440\u044b\u043d "
                    + date.getDayOfMonth();
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        };
    }

    /** Long form with the weekday, e.g. for a report or day heading. */
    public static String displayLong(String iso) {
        LocalDate date = fromIso(iso);
        if (date == null) {
            return iso == null ? "" : iso;
        }
        return displayLong(date);
    }

    public static String displayLong(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> date.getYear() + " \u043e\u043d\u044b " + monthNameOnly(date)
                    + " " + date.getDayOfMonth() + ", " + weekdayName(date);
            case ENGLISH -> weekdayName(date) + ", "
                    + date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));
        };
    }

    /** Day and month only, for lists where the year is obvious from context. */
    public static String displayShort(String iso) {
        LocalDate date = fromIso(iso);
        if (date == null) {
            return iso == null ? "" : iso;
        }
        return switch (Messages.current()) {
            case MONGOLIAN -> monthNameOnly(date) + " " + date.getDayOfMonth();
            case ENGLISH -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
        };
    }

    /** Month and year, e.g. for a calendar heading or report section. */
    public static String monthName(int year, int month) {
        LocalDate date = LocalDate.of(year, month, 1);
        return switch (Messages.current()) {
            case MONGOLIAN -> year + " \u043e\u043d\u044b " + monthNameOnly(date);
            case ENGLISH -> monthNameOnly(date) + " " + year;
        };
    }

    /** A short weekday name, for calendar columns and weekly plans. */
    public static String weekdayShort(LocalDate date) {
        return weekdayNameShort(date);
    }

    /** A full weekday name. */
    public static String weekdayLong(LocalDate date) {
        return weekdayName(date);
    }

    /** The month name on its own, in the selected language. */
    public static String monthOnly(LocalDate date) {
        return monthNameOnly(date);
    }

    // ------------------------------------------------------------------- parse

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

    /** Human wording for "how long ago", e.g. "3 days ago" / "today". */
    public static String relative(String iso) {
        LocalDate date = fromIso(iso);
        if (date == null) {
            return "";
        }
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        String count = String.valueOf(Math.abs(days));
        return switch (Messages.current()) {
            case ENGLISH -> {
                if (days == 0) {
                    yield "today";
                }
                if (days == 1) {
                    yield "yesterday";
                }
                if (days == -1) {
                    yield "tomorrow";
                }
                if (days > 1 && days < 30) {
                    yield days + " days ago";
                }
                if (days < -1 && days > -30) {
                    yield "in " + (-days) + " days";
                }
                yield display(iso);
            }
            case MONGOLIAN -> {
                if (days == 0) {
                    yield "\u04e9\u043d\u04e9\u04e9\u0434\u04e9\u0440";
                }
                if (days == 1) {
                    yield "\u04e9\u0447\u0438\u0433\u0434\u04e9\u0440";
                }
                if (days == -1) {
                    yield "\u043c\u0430\u0440\u0433\u0430\u0430\u0448";
                }
                if (days > 1 && days < 30) {
                    yield count + " \u04e9\u0434\u0440\u0438\u0439\u043d \u04e9\u043c\u043d\u04e9";
                }
                if (days < -1 && days > -30) {
                    yield count + " \u04e9\u0434\u0440\u0438\u0439\u043d \u0434\u0430\u0440\u0430\u0430";
                }
                yield display(iso);
            }
        };
    }

    /** A teacher-friendly date format for the report footer. */
    public static String displayForFile(LocalDate date) {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Messages.current().locale()));
    }

    // ------------------------------------------------------------------ ranges

    /** Inclusive start of the week (Monday) containing {@code date}. */
    public static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() + 6) % 7);
    }

    // --------------------------------------------------------------------- age

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
        return switch (Messages.current()) {
            case ENGLISH -> {
                if (years <= 0) {
                    yield rem + (rem == 1 ? " month" : " months");
                }
                if (rem == 0) {
                    yield years + (years == 1 ? " year" : " years");
                }
                yield years + (years == 1 ? " year " : " years ") + rem
                        + (rem == 1 ? " month" : " months");
            }
            case MONGOLIAN -> {
                if (years <= 0) {
                    yield months + " \u0441\u0430\u0440";
                }
                if (rem == 0) {
                    yield years + " \u043d\u0430\u0441";
                }
                yield years + " \u043d\u0430\u0441 " + rem + " \u0441\u0430\u0440";
            }
        };
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
        // Both the day-first and month-first numeric orders are accepted, along
        // with the written forms, so a teacher can type dates the way they think
        // of them regardless of the interface language.
        String[] patterns = {"d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "M/d/yyyy",
                "yyyy/M/d", "d MMM yyyy", "d MMMM yyyy",
                "yyyy '\u043e\u043d\u044b' M '\u0441\u0430\u0440\u044b\u043d' d",
                "yyyy '\u043e\u043d' M '\u0441\u0430\u0440' d"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(t, DateTimeFormatter.ofPattern(p, Messages.current().locale()));
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }
}
