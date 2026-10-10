package app.kinderjournal.i18n;

import java.util.Locale;

/**
 * The languages the interface can be shown in.
 *
 * <p>The choice is stored by its {@link #code()} in the database, so an
 * installation keeps the teacher's language between sessions. Adding a language
 * means adding a constant here and a catalog entry in {@link Messages}.
 */
public enum Lang {

    /** English (default). */
    ENGLISH("en", "English", "English", Locale.of("en", "GB")),

    /** Mongolian, written in Cyrillic, which is the standard script in Mongolia. */
    MONGOLIAN("mn", "Монгол", "Mongolian", Locale.of("mn", "MN"));

    private final String code;
    private final String nativeName;
    private final String englishName;
    private final Locale locale;

    Lang(String code, String nativeName, String englishName, Locale locale) {
        this.code = code;
        this.nativeName = nativeName;
        this.englishName = englishName;
        this.locale = locale;
    }

    /** Short code stored in the database, e.g. {@code "mn"}. */
    public String code() {
        return code;
    }

    /** The language's name in its own language, shown on the toggle button. */
    public String nativeName() {
        return nativeName;
    }

    /** The language's name in English, used in settings lists. */
    public String englishName() {
        return englishName;
    }

    /** Locale used for month names, day names and number formatting. */
    public Locale locale() {
        return locale;
    }

    /** True when text in this language is written in Cyrillic. */
    public boolean isCyrillic() {
        return this == MONGOLIAN;
    }

    /** The language to switch to when the toggle button is pressed. */
    public Lang other() {
        return this == ENGLISH ? MONGOLIAN : ENGLISH;
    }

    /** Looks up a language by its stored code, falling back to English. */
    public static Lang fromCode(String code) {
        if (code != null) {
            for (Lang lang : values()) {
                if (lang.code.equalsIgnoreCase(code.trim())) {
                    return lang;
                }
            }
        }
        return ENGLISH;
    }
}
