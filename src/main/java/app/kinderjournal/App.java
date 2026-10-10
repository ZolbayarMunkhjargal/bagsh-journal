package app.kinderjournal;

import app.kinderjournal.backup.BackupService;
import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * KinderJournal - a local journal for kindergarten teachers.
 *
 * <p>Everything the application owns (the SQLite database, attached photos,
 * backups and the error log) lives in one folder on this computer. There is no
 * account, no sync and no network access at any point.
 */
public class App extends Application {

    private Repository repository;

    @Override
    public void start(Stage stage) {
        try {
            repository = Repository.open();
            repository.ensureUsableStart();
        } catch (RuntimeException ex) {
            // Nothing else can be attempted without somewhere to keep the records,
            // so explain the problem in a plain window rather than failing silently.
            showStartupFailure(ex);
            Platform.exit();
            return;
        }

        AppContext context = new AppContext(repository);
        MainShell shell = new MainShell(context, stage);

        Scene scene = new Scene((javafx.scene.Parent) shell.root(), 1180, 760);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setOnKeyPressed(shell::handleShortcut);

        stage.setScene(scene);
        stage.setTitle(Messages.get("app.windowTitle"));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> closeQuietly());
        stage.show();

        shell.warnIfDataFolderIsFallback(stage);
        scheduleAutomaticBackup();
    }

    /**
     * Takes a dated backup in the background if the newest one is over a week old.
     *
     * <p>Done off the UI thread so start-up stays instant, and failures are only
     * logged: a backup that could not be written must never stop the teacher from
     * using the application.
     */
    private void scheduleAutomaticBackup() {
        Thread worker = new Thread(() -> {
            try {
                new BackupService(repository.db()).automaticBackupIfDue(7);
            } catch (IOException | RuntimeException ex) {
                Ui.logError("Automatic backup", ex);
            }
        }, "kinderjournal-autobackup");
        worker.setDaemon(true);
        worker.start();
    }

    private void closeQuietly() {
        if (repository == null) {
            return;
        }
        try {
            repository.db().checkpoint();
        } catch (RuntimeException ex) {
            Ui.logError("Flushing pending changes on exit", ex);
        }
        repository.close();
    }

    /**
     * Reports a failure that happened before the window could be built.
     *
     * <p>The usual alert needs JavaFX to be up and a data folder to write a log
     * into, and this path exists precisely because neither may be available. A
     * plain Swing dialog is used when possible, with the console as a fallback.
     */
    private static void showStartupFailure(Throwable ex) {
        String message = Messages.get("error.startupTitle") + "\n\n"
                + (ex.getMessage() == null ? ex.toString() : ex.getMessage())
                + "\n\n" + Messages.get("error.noRecordsChanged");
        System.err.println(message);
        ex.printStackTrace(System.err);
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                javax.swing.JTextArea area = new javax.swing.JTextArea(message, 16, 64);
                area.setEditable(false);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                javax.swing.JOptionPane.showMessageDialog(null, new javax.swing.JScrollPane(area),
                        Messages.get("error.startupTitle"), javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        } catch (Throwable ignored) {
            // The console output above is the last resort.
        }
    }

    @Override
    public void stop() {
        closeQuietly();
    }

    /** Entry point. */
    public static void main(String[] args) {
        if (hasFlag(args, "--data-dir")) {
            String value = valueOf(args, "--data-dir");
            if (value != null) {
                System.setProperty("kinderjournal.dataDir", value);
            }
        }
        // JavaFX and the JDK both format numbers and dates per locale, so the
        // default is aligned with the interface language. The stored preference is
        // applied later, in AppContext, once the database can be read.
        Locale.setDefault(Messages.current().locale());
        if (hasFlag(args, "--version")) {
            System.out.println(Messages.get("app.name") + " " + Messages.get("app.version")
                    + "  \u00b7  " + Messages.get("diag.dataFolder") + ": "
                    + AppPaths.dataDir().toAbsolutePath());
            return;
        }
        if (hasFlag(args, "--diagnostics")) {
            try {
                runDiagnostics(hasFlag(args, "--verbose"));
            } catch (RuntimeException ex) {
                // Even the report about the data file can fail when there is no
                // writable place to put one; say so plainly and exit cleanly.
                System.err.println("KinderJournal diagnostics could not run.");
                System.err.println(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                System.exit(2);
            }
            return;
        }
        if (hasFlag(args, "--seed-demo")) {
            try {
                int created = app.kinderjournal.tools.DemoData.seed(AppPaths.dataDir());
                System.out.println("Seeded demo data (" + created + " records) in "
                        + AppPaths.dataDir().toAbsolutePath());
            } catch (RuntimeException ex) {
                System.err.println("Sample data could not be created: " + ex.getMessage());
                System.exit(2);
            }
            System.out.flush();
            System.exit(0);
            return;
        }
        if (hasFlag(args, "--ui-test")) {
            new app.kinderjournal.tools.UiSmokeTest().run();
            return;
        }
        if (hasFlag(args, "--show")) {
            // Opens the real window on sample data, in the given language, so the
            // interface can be looked at rather than only asserted about.
            System.setProperty(app.kinderjournal.tools.DemoWindow.PROPERTY_LANGUAGE,
                    valueOf(args, "--show") == null ? "en" : valueOf(args, "--show"));
            String folder = valueOf(args, "--data-dir");
            if (folder != null) {
                System.setProperty(app.kinderjournal.tools.DemoWindow.PROPERTY_FOLDER, folder);
            }
            launch(app.kinderjournal.tools.DemoWindow.class, new String[0]);
            return;
        }
        if (hasFlag(args, "--export-reports")) {
            String value = valueOf(args, "--export-reports");
            Path target = value == null ? AppPaths.dataDir().resolve("reports") : Path.of(value);
            try {
                int written = app.kinderjournal.tools.ReportExporter.exportAll(target);
                System.out.println("Wrote " + written + " report(s) to " + target.toAbsolutePath());
            } catch (IOException ex) {
                System.out.println("Could not write the reports: " + ex.getMessage());
            }
            System.out.flush();
            System.exit(0);
            return;
        }
        launch(args);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }

    private static String valueOf(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * Prints a short report about the local data file - useful when a teacher
     * needs to send something to whoever supports the machine.
     *
     * @param verbose when true, a failure prints its full stack trace
     */
    private static void runDiagnostics(boolean verbose) {
        System.out.println(Messages.get("diag.title"));
        System.out.println("  " + pad(Messages.get("diag.dataFolder")) + ": " + AppPaths.dataDir().toAbsolutePath());
        System.out.println("  " + pad(Messages.get("diag.database")) + ": " + AppPaths.databaseFile());
        System.out.println("  " + pad(Messages.get("diag.photos")) + ": " + AppPaths.photosDir());
        System.out.println("  " + pad(Messages.get("diag.backups")) + ": " + AppPaths.backupsDir());
        System.out.println("  " + pad(Messages.get("diag.log")) + ": " + AppPaths.logFile());
        try (Repository repo = Repository.open()) {
            System.out.println("  " + pad(Messages.get("diag.integrity")) + ": " + repo.db().integrityCheck());
            repo.counts().forEach((key, value) -> System.out.println("  " + pad(key) + ": " + value));
            int missing = repo.photos().listMissingFiles().size();
            System.out.println("  " + pad(Messages.get("diag.missingPhotos")) + ": " + missing);
        } catch (RuntimeException ex) {
            System.out.println("  ERROR: " + ex.getMessage());
            if (verbose) {
                ex.printStackTrace(System.out);
            }
        }
        System.out.flush();
        // The SQLite driver keeps a native thread alive; exit explicitly so a
        // support session is not left with a process that never finishes.
        System.exit(0);
    }

    private static String pad(String text) {
        return String.format("%-18s", text);
    }

    /** Shows the raw error detail in a copyable window (used for hard failures). */
    static void showFatal(String title, String message, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Ui.APP_NAME);
        alert.setHeaderText(title);
        TextArea area = new TextArea(message + "\n\n" + ex);
        area.setEditable(false);
        area.setPrefRowCount(14);
        area.setPrefColumnCount(70);
        alert.getDialogPane().setContent(area);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.showAndWait();
    }
}
