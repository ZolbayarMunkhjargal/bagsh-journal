package app.kinderjournal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves and prepares the folder where the app keeps everything it owns.
 *
 * <p>Layout:
 * <pre>
 *   &lt;data&gt;/
 *     kinderjournal.db      SQLite database
 *     photos/               attached images, named by a generated id
 *     backups/              automatic copies taken before risky operations
 *     logs/                 error log
 * </pre>
 *
 * <p>The location can be overridden with the environment variable
 * {@code KINDERJOURNAL_DATA_DIR} (used by the tests) or with the
 * {@code -Dkinderjournal.dataDir=...} system property.
 */
public final class AppPaths {

    /** Environment variable that overrides the data folder. */
    public static final String ENV_DATA_DIR = "KINDERJOURNAL_DATA_DIR";

    private static final String APP_FOLDER = "KinderJournal";

    private static Path dataDir;
    private static boolean usingFallback;

    private AppPaths() {
    }

    /**
     * The data folder, created if needed.
     *
     * <p>If the preferred location cannot be written to (locked-down machine,
     * read-only roaming profile, full disk), the next candidate is used instead
     * and {@link #isUsingFallback()} reports true so the UI can tell the teacher.
     */
    public static synchronized Path dataDir() {
        if (dataDir != null) {
            return dataDir;
        }
        List<Path> candidates = new ArrayList<>();

        String override = System.getProperty("kinderjournal.dataDir");
        if (Text.isBlank(override)) {
            override = System.getenv(ENV_DATA_DIR);
        }
        boolean forced = !Text.isBlank(override);
        if (forced) {
            candidates.add(Paths.get(override).toAbsolutePath().normalize());
        } else {
            String appData = System.getenv("APPDATA");
            if (!Text.isBlank(appData)) {
                candidates.add(Paths.get(appData, APP_FOLDER).toAbsolutePath().normalize());
            }
            String home = System.getProperty("user.home");
            if (!Text.isBlank(home)) {
                candidates.add(Paths.get(home, "." + APP_FOLDER.toLowerCase()).toAbsolutePath().normalize());
            }
            candidates.add(Paths.get(System.getProperty("java.io.tmpdir"), APP_FOLDER).toAbsolutePath().normalize());
        }

        IOException last = null;
        List<String> tried = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Path candidate = candidates.get(i);
            tried.add(candidate.toString());
            try {
                prepare(candidate);
                dataDir = candidate;
                usingFallback = !forced && i > 0;
                return dataDir;
            } catch (IOException ex) {
                last = ex;
            }
        }
        // Naming every location that was tried makes the failure actionable: the
        // teacher, or whoever supports the computer, can see what to check.
        throw new UncheckedIOException(new IOException(
                "KinderJournal could not create a folder to keep its records in. It tried:" + "\n  "
                        + String.join("\n  ", tried)
                        + "\n\nCheck that you can write to one of those locations, then start the "
                        + "program again. No records have been changed.", last));
    }

    private static void prepare(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("photos"));
        Files.createDirectories(dir.resolve("backups"));
        Files.createDirectories(dir.resolve("logs"));
        // Prove the folder is genuinely writable rather than merely present.
        Path probe = dir.resolve(".write-test");
        Files.writeString(probe, "ok");
        Files.deleteIfExists(probe);
    }

    /** True when the preferred data folder was unavailable and a fallback is in use. */
    public static synchronized boolean isUsingFallback() {
        dataDir();
        return usingFallback;
    }

    public static Path databaseFile() {
        return dataDir().resolve("kinderjournal.db");
    }

    public static Path photosDir() {
        return dataDir().resolve("photos");
    }

    public static Path backupsDir() {
        return dataDir().resolve("backups");
    }

    public static Path logsDir() {
        return dataDir().resolve("logs");
    }

    public static Path logFile() {
        return logsDir().resolve("kinderjournal.log");
    }

    /** A copy of the database taken before a risky operation, e.g. restore. */
    public static Path safetyCopy(String label) {
        String stamp = Dates.ISO.format(LocalDate.now()) + "-" + System.currentTimeMillis() % 100000;
        String safe = label.replaceAll("[^A-Za-z0-9._-]", "-");
        return backupsDir().resolve(safe + "-" + stamp + ".db");
    }

    /** A new, unused file name (not path) for an attached photo. */
    public static String newPhotoName(String originalName) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String candidate = originalName.substring(dot + 1).toLowerCase();
                if (candidate.matches("[a-z0-9]{1,5}")) {
                    ext = "." + candidate;
                }
            }
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    /** Copies a file into the local photos folder, returning the stored file name. */
    public static String storePhoto(Path source) throws IOException {
        String name = newPhotoName(source.getFileName().toString());
        Files.copy(source, photosDir().resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    /** Writes an image supplied as a stream (used by tests and imports). */
    public static String storePhoto(InputStream source, String originalName) throws IOException {
        String name = newPhotoName(originalName);
        Files.copy(source, photosDir().resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public static Path photoFile(String storedName) {
        return storedName == null ? null : photosDir().resolve(storedName);
    }

    /** True when the stored photo is still on disk. */
    public static boolean photoExists(String storedName) {
        if (Text.isBlank(storedName)) {
            return false;
        }
        Path p = photoFile(storedName);
        return p != null && Files.isRegularFile(p);
    }
}
