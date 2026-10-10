package app.kinderjournal.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Looks up interface text in the currently selected language.
 *
 * <p>Text lives in plain {@code .properties} files beside this class so it can be
 * corrected or extended without touching Java code. English is the master list:
 * anything the active language does not translate falls back to English, and a
 * key missing from both is returned as-is so a mistake is visible on screen
 * rather than showing up as an empty label.
 *
 * <p>Screens are built from these strings, so changing language means asking the
 * window to rebuild itself; {@link #onChange(Runnable)} is how they are told.
 */
public final class Messages {

    private static final String BASE = "/app/kinderjournal/i18n/messages";

    private static final Map<Lang, Properties> CATALOGS = new LinkedHashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private static Lang current = Lang.ENGLISH;

    private Messages() {
    }

    // ------------------------------------------------------------------ language

    /** The language the interface is currently shown in. */
    public static Lang current() {
        return current;
    }

    /**
     * Switches language and tells the interface to rebuild.
     *
     * @return true when the language actually changed
     */
    public static boolean setCurrent(Lang lang) {
        if (lang == null || lang == current) {
            return false;
        }
        current = lang;
        for (Runnable listener : new ArrayList<>(LISTENERS)) {
            listener.run();
        }
        return true;
    }

    /** Switches to the other language; used by the toggle button. */
    public static boolean toggle() {
        return setCurrent(current.other());
    }

    /** Registers something to run whenever the language changes. */
    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    // -------------------------------------------------------------------- lookup

    /** The text for a key in the current language. */
    public static String get(String key) {
        if (key == null) {
            return "";
        }
        Properties active = catalog(current);
        String value = active.getProperty(key);
        if (value != null) {
            return value;
        }
        String fallback = catalog(Lang.ENGLISH).getProperty(key);
        // Returning the key makes a missing translation obvious during development
        // instead of leaving a blank label on screen.
        return fallback != null ? fallback : key;
    }

    /**
     * The text for a key with {@code {0}}, {@code {1}} ... replaced by values.
     *
     * <p>Substitution is done here rather than with {@code MessageFormat} because
     * the Mongolian translations contain no formatting codes of their own, and a
     * missing placeholder should leave the original text alone rather than throw.
     */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0 || pattern.indexOf('{') < 0) {
            return pattern;
        }
        String result = pattern;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String value = args[i] == null ? "" : String.valueOf(args[i]);
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * The text for a key in a specific language, ignoring the current selection.
     * Used by the language menu, which must show each language in its own script.
     */
    public static String in(Lang lang, String key) {
        String value = catalog(lang).getProperty(key);
        if (value != null) {
            return value;
        }
        String fallback = catalog(Lang.ENGLISH).getProperty(key);
        return fallback != null ? fallback : key;
    }

    /** True when the given language has its own translation for a key. */
    public static boolean has(Lang lang, String key) {
        return catalog(lang).getProperty(key) != null;
    }

    /** Every key the English catalog defines, in file order. */
    public static List<String> keys() {
        Properties english = catalog(Lang.ENGLISH);
        List<String> keys = new ArrayList<>();
        for (String name : english.stringPropertyNames()) {
            keys.add(name);
        }
        keys.sort(String::compareTo);
        return keys;
    }

    // ------------------------------------------------------------------ catalogs

    private static Properties catalog(Lang lang) {
        return CATALOGS.computeIfAbsent(lang, Messages::load);
    }

    private static Properties load(Lang lang) {
        Properties properties = new Properties();
        String resource = BASE + "_" + lang.code() + ".properties";
        try (InputStream in = Messages.class.getResourceAsStream(resource)) {
            if (in == null) {
                // A language without a catalog still works: every lookup falls
                // back to English rather than failing.
                return properties;
            }
            // Read as UTF-8 so Cyrillic text is not mangled; the Properties
            // load(Reader) form keeps the caller in charge of the encoding.
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            // Leave the catalog empty; get() falls back to English.
        }
        return properties;
    }

    /** Reloads the catalogs, so an edited file can be picked up without a restart. */
    public static void reload() {
        CATALOGS.clear();
    }

    /**
     * Keys the given language has not translated yet.
     *
     * <p>Used by the tests to report how complete a translation is, and by the
     * Data screen to warn about a partial one.
     */
    public static List<String> missingFor(Lang lang) {
        List<String> missing = new ArrayList<>();
        for (String key : keys()) {
            if (!has(lang, key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}
