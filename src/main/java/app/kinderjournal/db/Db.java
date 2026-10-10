package app.kinderjournal.db;

import app.kinderjournal.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the SQLite connection, the schema, and the transaction helpers.
 *
 * <p>The whole application is single-user and single-process, so one shared
 * connection is enough. SQLite is put into WAL mode with foreign keys enabled:
 * WAL keeps writes fast and crash-safe, and the foreign keys stop an orphaned
 * observation or photo from ever being created.
 */
public final class Db implements AutoCloseable {

    /** Bumped whenever the schema changes; see {@link #migrate}. */
    public static final int SCHEMA_VERSION = 1;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String url;
    private final Path databasePath;
    private Connection connection;

    private Db(String url, Path databasePath) {
        this.url = url;
        this.databasePath = databasePath;
    }

    // ------------------------------------------------------------- constructors

    /** Opens the real database in the application data folder. */
    public static Db open() {
        Path file = AppPaths.databaseFile();
        Db db = new Db("jdbc:sqlite:" + file.toAbsolutePath(), file);
        db.connect();
        return db;
    }

    /** Opens (or creates) a database at an explicit path - used by the tests. */
    public static Db openAt(Path file) {
        Db db = new Db("jdbc:sqlite:" + file.toAbsolutePath(), file);
        db.connect();
        return db;
    }

    /** Opens a throwaway in-memory database - used by the tests. */
    public static Db openInMemory() {
        Db db = new Db("jdbc:sqlite::memory:", null);
        db.connect();
        return db;
    }

    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new DataException("Loading the SQLite driver", ex);
        }
        try {
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 10000");
                st.execute("PRAGMA synchronous = FULL");
            }
            migrate();
        } catch (SQLException ex) {
            throw new DataException("Opening the database at " + url, ex);
        }
    }

    /** The live connection. */
    public Connection connection() {
        return connection;
    }

    public Path path() {
        return databasePath;
    }

    // ------------------------------------------------------------------- schema

    private void migrate() {
        int version = userVersion();
        if (version == 0) {
            createSchema();
            setUserVersion(SCHEMA_VERSION);
            return;
        }
        if (version > SCHEMA_VERSION) {
            throw new DataException("This data file was created by a newer version of "
                    + "KinderJournal (schema " + version + ", this build understands "
                    + SCHEMA_VERSION + "). Please update the application.");
        }
        // Future schema upgrades go here as `if (version < N) { ... }` steps.
    }

    private int userVersion() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new DataException("Reading the schema version", ex);
        }
    }

    private void setUserVersion(int version) {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        } catch (SQLException ex) {
            throw new DataException("Recording the schema version", ex);
        }
    }

    private void createSchema() {
        List<String> statements = new ArrayList<>();
        statements.add("""
                CREATE TABLE IF NOT EXISTS groups (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    name          TEXT NOT NULL,
                    description   TEXT NOT NULL DEFAULT '',
                    age_range     TEXT NOT NULL DEFAULT '',
                    room          TEXT NOT NULL DEFAULT '',
                    teacher       TEXT NOT NULL DEFAULT '',
                    school_year   TEXT NOT NULL DEFAULT '',
                    archived      INTEGER NOT NULL DEFAULT 0,
                    sort_order    INTEGER NOT NULL DEFAULT 0,
                    created_at    TEXT NOT NULL DEFAULT '',
                    updated_at    TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS children (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    first_name        TEXT NOT NULL DEFAULT '',
                    last_name         TEXT NOT NULL DEFAULT '',
                    birth_date        TEXT,
                    gender            TEXT NOT NULL DEFAULT '',
                    group_id          INTEGER REFERENCES groups(id) ON DELETE SET NULL,
                    start_date        TEXT,
                    guardian_name     TEXT NOT NULL DEFAULT '',
                    guardian_relation TEXT NOT NULL DEFAULT '',
                    guardian_phone    TEXT NOT NULL DEFAULT '',
                    guardian_email    TEXT NOT NULL DEFAULT '',
                    second_contact    TEXT NOT NULL DEFAULT '',
                    address           TEXT NOT NULL DEFAULT '',
                    allergies         TEXT NOT NULL DEFAULT '',
                    notes             TEXT NOT NULL DEFAULT '',
                    photo_file        TEXT,
                    archived          INTEGER NOT NULL DEFAULT 0,
                    created_at        TEXT NOT NULL DEFAULT '',
                    updated_at        TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    child_id       INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
                    group_id       INTEGER REFERENCES groups(id) ON DELETE SET NULL,
                    date           TEXT NOT NULL,
                    status         TEXT NOT NULL DEFAULT 'present',
                    reason         TEXT NOT NULL DEFAULT '',
                    note           TEXT NOT NULL DEFAULT '',
                    arrival_time   TEXT NOT NULL DEFAULT '',
                    departure_time TEXT NOT NULL DEFAULT '',
                    recorded_at    TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("CREATE UNIQUE INDEX IF NOT EXISTS idx_attendance_child_date "
                + "ON attendance(child_id, date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_attendance_group_date "
                + "ON attendance(group_id, date)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS observations (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    child_id     INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
                    group_id     INTEGER REFERENCES groups(id) ON DELETE SET NULL,
                    date         TEXT NOT NULL,
                    category     TEXT NOT NULL DEFAULT 'general',
                    title        TEXT NOT NULL DEFAULT '',
                    body         TEXT NOT NULL DEFAULT '',
                    context      TEXT NOT NULL DEFAULT '',
                    author       TEXT NOT NULL DEFAULT '',
                    confidential INTEGER NOT NULL DEFAULT 0,
                    created_at   TEXT NOT NULL DEFAULT '',
                    updated_at   TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_observations_child_date "
                + "ON observations(child_id, date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_observations_date ON observations(date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_observations_category ON observations(category)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS interviews (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    child_id      INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
                    group_id      INTEGER REFERENCES groups(id) ON DELETE SET NULL,
                    date          TEXT NOT NULL,
                    type          TEXT NOT NULL DEFAULT 'parent',
                    mode          TEXT NOT NULL DEFAULT 'in-person',
                    participants  TEXT NOT NULL DEFAULT '',
                    topics        TEXT NOT NULL DEFAULT '',
                    discussed     TEXT NOT NULL DEFAULT '',
                    conclusions   TEXT NOT NULL DEFAULT '',
                    follow_up     TEXT NOT NULL DEFAULT '',
                    follow_up_date TEXT,
                    follow_up_done INTEGER NOT NULL DEFAULT 0,
                    created_at    TEXT NOT NULL DEFAULT '',
                    updated_at    TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_interviews_child_date "
                + "ON interviews(child_id, date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_interviews_date ON interviews(date)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS lesson_plans (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id     INTEGER REFERENCES groups(id) ON DELETE SET NULL,
                    title        TEXT NOT NULL DEFAULT '',
                    date         TEXT NOT NULL,
                    end_date     TEXT,
                    start_time   TEXT NOT NULL DEFAULT '',
                    end_time     TEXT NOT NULL DEFAULT '',
                    area         TEXT NOT NULL DEFAULT 'other',
                    objectives   TEXT NOT NULL DEFAULT '',
                    description  TEXT NOT NULL DEFAULT '',
                    materials    TEXT NOT NULL DEFAULT '',
                    sequence     TEXT NOT NULL DEFAULT '',
                    instructions TEXT NOT NULL DEFAULT '',
                    status       TEXT NOT NULL DEFAULT 'planned',
                    reflection   TEXT NOT NULL DEFAULT '',
                    template     INTEGER NOT NULL DEFAULT 0,
                    recurring    INTEGER NOT NULL DEFAULT 0,
                    sort_order   INTEGER NOT NULL DEFAULT 0,
                    created_at   TEXT NOT NULL DEFAULT '',
                    updated_at   TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_plans_date ON lesson_plans(date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_plans_group_date "
                + "ON lesson_plans(group_id, date)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_plans_template ON lesson_plans(template)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS photos (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_type TEXT NOT NULL,
                    owner_id   INTEGER NOT NULL,
                    child_id   INTEGER REFERENCES children(id) ON DELETE CASCADE,
                    file       TEXT NOT NULL,
                    caption    TEXT NOT NULL DEFAULT '',
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    added_at   TEXT NOT NULL DEFAULT ''
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_photos_owner ON photos(owner_type, owner_id)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_photos_child ON photos(child_id)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL DEFAULT ''
                )""");

        try (Statement st = connection.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        } catch (SQLException ex) {
            throw new DataException("Creating the database tables", ex);
        }
        saveSetting("created_at", now());
    }

    // ------------------------------------------------------------- transactions

    /**
     * Runs a query and maps each row.
     *
     * <p>The mapper is given a {@link Row} rather than the raw result set so that
     * mapping code cannot accidentally leave the statement open.
     */
    public <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(new Row(rs)));
                }
                return out;
            }
        } catch (SQLException ex) {
            throw new DataException("Reading from the database", ex);
        }
    }

    /** Runs a query expecting at most one row. */
    public <T> T one(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = list(sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Runs a query returning a single long (e.g. a COUNT). */
    public long count(String sql, Object... params) {
        Long value = one(sql, row -> row.getLong("n"), params);
        return value == null ? 0L : value;
    }

    /** Runs an INSERT/UPDATE/DELETE and returns the number of affected rows. */
    public int update(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataException("Saving to the database", ex);
        }
    }

    /** Runs an INSERT and returns the generated row id. */
    public long insert(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DataException("Adding a record", ex);
        }
    }

    /**
     * Runs {@code work} inside a transaction, rolling back if anything throws.
     * Nested calls join the outer transaction.
     */
    public <T> T transaction(TransactionWork<T> work) {
        boolean owner = false;
        try {
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
                owner = true;
            }
            T result = work.run();
            if (owner) {
                connection.commit();
            }
            return result;
        } catch (SQLException ex) {
            throw new DataException("Saving changes", ex);
        } catch (RuntimeException ex) {
            if (owner) {
                rollbackQuietly();
            }
            throw ex;
        } finally {
            if (owner) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // The connection is about to be discarded; nothing useful to do.
                }
            }
        }
    }

    /** Convenience wrapper for work that does not return a value. */
    public void run(Runnable work) {
        transaction(() -> {
            work.run();
            return null;
        });
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort: the original failure is the one worth reporting.
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                ps.setObject(index, null);
            } else if (value instanceof Long v) {
                ps.setLong(index, v);
            } else if (value instanceof Integer v) {
                ps.setInt(index, v);
            } else if (value instanceof Boolean v) {
                ps.setInt(index, v ? 1 : 0);
            } else if (value instanceof Double v) {
                ps.setDouble(index, v);
            } else {
                ps.setString(index, value.toString());
            }
        }
    }

    // ------------------------------------------------------------------ settings

    public String setting(String key, String fallback) {
        String value = one("SELECT value FROM settings WHERE key = ?", row -> row.getString("value"), key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    public void saveSetting(String key, String value) {
        update("INSERT INTO settings(key, value) VALUES(?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value == null ? "" : value);
    }

    // ------------------------------------------------------- integrity and backup

    /** Runs SQLite's own consistency check; returns "ok" when the file is sound. */
    public String integrityCheck() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(rs.getString(1));
            }
            return sb.length() == 0 ? "ok" : sb.toString();
        } catch (SQLException ex) {
            throw new DataException("Checking the database", ex);
        }
    }

    /**
     * Flushes pending writes into the main database file, folds the write-ahead
     * log back in, and returns a compacted copy at {@code target}.
     *
     * <p>Using SQLite's own {@code VACUUM INTO} means the copy is a consistent
     * snapshot even if a write were in flight.
     */
    public void backupTo(Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new DataException("Preparing the backup file", ex);
        }
        // VACUUM INTO cannot run inside a transaction and needs a literal path.
        String escaped = target.toAbsolutePath().toString().replace("'", "''");
        try (Statement st = connection.createStatement()) {
            st.execute("VACUUM INTO '" + escaped + "'");
        } catch (SQLException ex) {
            throw new DataException("Writing the backup", ex);
        }
    }

    /**
     * Replaces the live database with the file at {@code source}.
     *
     * <p>The caller is responsible for taking a safety copy first; this method
     * only swaps the file and reopens it. Because SQLite keeps the file handle
     * open, the connection is closed and the journal files are cleared, so a
     * stale {@code -wal} can never be applied to the restored data.
     */
    public void replaceWith(Path source) {
        if (databasePath == null) {
            throw new DataException("An in-memory database cannot be replaced from a file");
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new DataException("Closing the database before restore", ex);
        }
        try {
            Files.copy(source, databasePath, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(Path.of(databasePath + "-wal"));
            Files.deleteIfExists(Path.of(databasePath + "-shm"));
        } catch (IOException ex) {
            throw new DataException("Replacing the database file", ex);
        }
        connect();
    }

    /** Runs {@code checkpoint} so the on-disk file is complete before a plain copy. */
    public void checkpoint() {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException ex) {
            throw new DataException("Flushing pending changes", ex);
        }
    }

    /** Applies {@code work} to every row of a query, for bulk maintenance. */
    public <T> void forEach(String sql, RowMapper<T> mapper, Consumer<T> action, Object... params) {
        for (T item : list(sql, mapper, params)) {
            action.accept(item);
        }
    }

    /** Timestamp format used for created_at / updated_at / recorded_at columns. */
    public static String now() {
        return STAMP.format(LocalDateTime.now());
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while shutting down.
        }
    }

    /** Maps one result-set row to a domain object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(Row row) throws SQLException;
    }

    /** A unit of work run inside a transaction. */
    @FunctionalInterface
    public interface TransactionWork<T> {
        T run() throws SQLException;
    }
}
