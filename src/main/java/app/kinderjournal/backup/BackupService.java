package app.kinderjournal.backup;

import app.kinderjournal.db.Db;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Dates;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backing up and restoring the whole local data folder.
 *
 * <p>A backup is a single {@code .kjbackup} file (a ZIP) holding the SQLite
 * database plus every attached photo, so a teacher can copy one file to a USB
 * stick or a network drive and be sure nothing was left behind. Restoring always
 * takes a safety copy of what is already there first, so a mistaken restore can
 * itself be undone.
 */
public final class BackupService {

    /** File extension used for backups. */
    public static final String EXTENSION = ".kjbackup";

    private static final String DB_ENTRY = "kinderjournal.db";
    private static final String PHOTO_PREFIX = "photos/";
    private static final String MANIFEST = "backup.txt";
    private static final int KEEP_AUTOMATIC = 5;

    /** What a completed backup contains. */
    public record BackupResult(Path file, int photos, long bytes, String databaseIntegrity) {

        /** A readable one-line description of the backup. */
        public String summary() {
            String photoText = photos == 1 ? "1 photo" : photos + " photos";
            return photoText + ", " + sizeText() + ", database " + databaseIntegrity;
        }

        public String sizeText() {
            return readableSize(bytes);
        }
    }

    /** What a restore did. */
    public record RestoreResult(Path safetyCopy, int photos, String databaseIntegrity) {
    }

    private final Db db;

    public BackupService(Db db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ backup

    /**
     * Writes a complete backup to {@code target}.
     *
     * <p>The database is copied with SQLite's own {@code VACUUM INTO}, which
     * produces a consistent snapshot even if something were being written at the
     * time, and each photo is streamed into the archive.
     */
    public BackupResult backupTo(Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path snapshot = Files.createTempFile("kinderjournal-snapshot", ".db");
        String integrity;
        try {
            db.backupTo(snapshot);
            integrity = db.integrityCheck();
            int photos = 0;
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(target.toAbsolutePath()))) {
                zip.putNextEntry(new ZipEntry(MANIFEST));
                zip.write(manifest().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry(DB_ENTRY));
                Files.copy(snapshot, zip);
                zip.closeEntry();

                if (Files.isDirectory(AppPaths.photosDir())) {
                    try (var stream = Files.list(AppPaths.photosDir())) {
                        for (Path photo : stream.filter(Files::isRegularFile).toList()) {
                            zip.putNextEntry(new ZipEntry(PHOTO_PREFIX + photo.getFileName()));
                            Files.copy(photo, zip);
                            zip.closeEntry();
                            photos++;
                        }
                    }
                }
            }
            return new BackupResult(target, photos, Files.size(target), integrity);
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    /** A default file name for a new backup, e.g. {@code KinderJournal-2026-03-02.kjbackup}. */
    public static Path suggestedFile() {
        String stamp = Dates.ISO.format(LocalDate.now());
        return AppPaths.backupsDir().resolve("KinderJournal-" + stamp + EXTENSION);
    }

    private String manifest() {
        StringBuilder sb = new StringBuilder();
        sb.append("KinderJournal backup\n");
        sb.append("created_date=").append(Dates.ISO.format(LocalDate.now())).append('\n');
        sb.append("created_time=").append(java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))).append('\n');
        sb.append("schema=").append(Db.SCHEMA_VERSION).append('\n');
        sb.append("source=").append(AppPaths.dataDir().toAbsolutePath()).append('\n');
        sb.append("contains=SQLite database and all attached photos\n");
        sb.append("\nRestore this file from KinderJournal's \"Data & backup\" screen.\n");
        return sb.toString();
    }

    // ----------------------------------------------------------------- restore

    /**
     * Replaces the current data with the contents of {@code source}.
     *
     * <p>Order matters: the archive is validated and unpacked into a staging
     * folder first, a safety copy of the live database is taken second, and only
     * then is the live data swapped. A corrupt or unrelated file is therefore
     * rejected without touching what the teacher already has.
     */
    public RestoreResult restoreFrom(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) < 512) {
            throw new IOException("That file does not look like a KinderJournal backup.");
        }
        Path staging = Files.createTempDirectory("kinderjournal-restore");
        try {
            int photos = unpack(source, staging);
            Path stagedDb = staging.resolve(DB_ENTRY);
            if (!Files.isRegularFile(stagedDb)) {
                throw new IOException("The backup does not contain a KinderJournal database.");
            }
            String stagedIntegrity = verify(stagedDb);
            if (!"ok".equalsIgnoreCase(stagedIntegrity)) {
                throw new IOException("The database inside the backup is damaged ("
                        + stagedIntegrity + "). Nothing was changed.");
            }

            // Safety copy of the live data before anything is replaced.
            Path safety = AppPaths.safetyCopy("before-restore");
            Files.createDirectories(safety.getParent());
            if (Files.isRegularFile(AppPaths.databaseFile())) {
                db.backupTo(safety);
            }

            db.replaceWith(stagedDb);
            int restoredPhotos = replacePhotos(staging.resolve("photos"));

            return new RestoreResult(safety, restoredPhotos, db.integrityCheck());
        } finally {
            deleteRecursively(staging);
        }
    }

    /** Unpacks an archive, refusing entries that try to escape the folder. */
    private int unpack(Path source, Path staging) throws IOException {
        int photos = 0;
        Path photosDir = staging.resolve("photos");
        Files.createDirectories(photosDir);
        try (InputStream raw = Files.newInputStream(source);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.contains("..")) {
                    throw new IOException("The backup contains an unsafe file name: " + name);
                }
                if (name.equals(DB_ENTRY)) {
                    Files.copy(zip, staging.resolve(DB_ENTRY), StandardCopyOption.REPLACE_EXISTING);
                } else if (name.startsWith(PHOTO_PREFIX)) {
                    String fileName = name.substring(PHOTO_PREFIX.length());
                    if (fileName.isBlank() || fileName.contains("/")) {
                        continue; // no nested folders inside photos/
                    }
                    Files.copy(zip, photosDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    photos++;
                }
                zip.closeEntry();
            }
        }
        return photos;
    }

    /** Replaces the photos folder, keeping any file not present in the backup. */
    private int replacePhotos(Path stagedPhotos) throws IOException {
        Path live = AppPaths.photosDir();
        Files.createDirectories(live);
        if (!Files.isDirectory(stagedPhotos)) {
            return 0;
        }
        int count = 0;
        try (var stream = Files.list(stagedPhotos)) {
            for (Path staged : stream.filter(Files::isRegularFile).toList()) {
                Files.copy(staged, live.resolve(staged.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    /** Opens the staged database and runs SQLite's own consistency check. */
    private String verify(Path database) {
        try (Db probe = Db.openAt(database)) {
            probe.saveSetting("last_restore_check", Db.now());
            return probe.integrityCheck();
        } catch (RuntimeException ex) {
            return "could not be opened (" + ex.getMessage() + ")";
        }
    }

    // ------------------------------------------------------- automatic backups

    /**
     * Takes a dated backup when the newest one is older than {@code maxAgeDays}.
     *
     * <p>Called at start-up so a teacher who never remembers to press the button
     * still has a recent copy. Old automatic copies are pruned, but a backup the
     * teacher made by hand is left alone.
     */
    public Path automaticBackupIfDue(int maxAgeDays) throws IOException {
        long newest = 0L;
        for (Path file : listBackups()) {
            newest = Math.max(newest, modified(file));
        }
        if (newest > 0) {
            LocalDate newestDate = java.time.Instant.ofEpochMilli(newest)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (newestDate.plusDays(maxAgeDays).isAfter(LocalDate.now())) {
                return null;
            }
        }
        Path target = AppPaths.backupsDir().resolve(
                "automatic-" + Dates.ISO.format(LocalDate.now()) + EXTENSION);
        BackupResult result = backupTo(target);
        pruneAutomatic();
        return result.file();
    }

    /** Every backup in the backups folder, newest first. */
    public static List<Path> listBackups() {
        Path dir = AppPaths.backupsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>(stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .toList());
            files.sort(Comparator.comparingLong(BackupService::modified).reversed());
            return files;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    /** Keeps only the most recent automatic backups. */
    private static void pruneAutomatic() {
        List<Path> automatic = new ArrayList<>();
        for (Path file : listBackups()) {
            if (file.getFileName().toString().startsWith("automatic-")) {
                automatic.add(file);
            }
        }
        for (int i = KEEP_AUTOMATIC; i < automatic.size(); i++) {
            try {
                Files.deleteIfExists(automatic.get(i));
            } catch (IOException ignored) {
                // A leftover backup is harmless.
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Total size of the data folder, for the storage summary. */
    public static long dataFolderSize() {
        long total = 0;
        total += size(AppPaths.databaseFile());
        total += folderSize(AppPaths.photosDir());
        total += folderSize(AppPaths.backupsDir());
        return total;
    }

    private static long folderSize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(BackupService::size).sum();
        } catch (IOException ex) {
            return 0;
        }
    }

    private static long size(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0L;
        } catch (IOException ex) {
            return 0L;
        }
    }

    /** A readable size such as "12.4 MB". */
    public static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    /** Removes a folder and everything in it (used for the staging folder). */
    static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort while cleaning up.
                }
            });
        } catch (IOException ignored) {
            // The staging folder lives in the temporary area; the OS tidies it.
        }
    }

    /** True when the file looks like a KinderJournal backup (a ZIP archive). */
    public static boolean looksLikeBackup(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(4);
            return header.length == 4 && header[0] == 'P' && header[1] == 'K';
        } catch (IOException ex) {
            return false;
        }
    }
}
