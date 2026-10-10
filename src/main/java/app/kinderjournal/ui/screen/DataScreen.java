package app.kinderjournal.ui.screen;

import app.kinderjournal.backup.BackupService;
import app.kinderjournal.model.Photo;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Data and backup: where everything is stored, how to copy it somewhere safe, how
 * to bring it back, and a few maintenance checks.
 *
 * <p>This screen is deliberately explicit about the fact that the data is only on
 * this computer: the folder is shown in full, the size is displayed, and backups
 * are one click. Photos whose files have gone missing are listed here so the
 * problem is visible rather than silently hidden.
 */
public final class DataScreen implements Refreshable {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    public DataScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        refresh();
    }

    @Override
    public String title() {
        return "Data & backup";
    }

    @Override
    public String subtitle() {
        return "Everything is stored on this computer. Back it up regularly.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        VBox page = Ui.page();
        page.getChildren().add(buildWhereCard());
        page.getChildren().add(buildBackupCard());
        page.getChildren().add(buildRestoreCard());
        page.getChildren().add(buildHealthCard());
        page.getChildren().add(buildPersonalisationCard());
        root.setContent(page);
    }

    // ----------------------------------------------------------------- storage

    private Node buildWhereCard() {
        Map<String, Integer> counts = context.repo().counts();
        VBox details = new VBox(6);

        TextField path = new TextField(context.repo().dataLocationText());
        path.setEditable(false);
        path.setPrefColumnCount(52);

        HBox pathRow = new HBox(8, path, Ui.button("Open this folder", this::openDataFolder));
        pathRow.setAlignment(Pos.CENTER_LEFT);

        details.getChildren().addAll(
                Ui.fieldLabel("Data folder"),
                pathRow,
                Ui.meta("Database: " + AppPaths.databaseFile().getFileName()
                        + "  \u00b7  Photos: " + AppPaths.photosDir().getFileName()
                        + "  \u00b7  Size on disk: "
                        + BackupService.readableSize(BackupService.dataFolderSize())));

        VBox counts_ = new VBox(4);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            counts_.getChildren().add(Ui.keyValue(entry.getKey(), String.valueOf(entry.getValue())));
        }
        details.getChildren().add(counts_);

        details.getChildren().add(Ui.meta("KinderJournal makes no network connections. Nothing "
                + "here is uploaded, synchronised or shared automatically."));
        return Ui.card("Where your data lives", details);
    }

    private void openDataFolder() {
        Path dir = AppPaths.dataDir();
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (IOException | UnsupportedOperationException ex) {
            Ui.logError("Opening the data folder", ex);
        }
        Ui.info("Your data folder", dir.toAbsolutePath().toString());
    }

    // ------------------------------------------------------------------ backup

    private Node buildBackupCard() {
        VBox box = new VBox(10);

        box.getChildren().add(Ui.meta("A backup is one file containing the database and every "
                + "attached photo. Keep a copy on a USB stick, an external disk or your school's "
                + "secure drive."));

        HBox actions = new HBox(8,
                Ui.primaryButton("Create a backup\u2026", this::createBackup),
                Ui.button("Back up into the app folder", this::quickBackup),
                Ui.button("Open the backups folder", this::openBackupsFolder));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);

        List<Path> backups = BackupService.listBackups();
        VBox list = new VBox(6);
        if (backups.isEmpty()) {
            list.getChildren().add(Ui.meta("No backups yet. Create one now so a broken computer "
                    + "cannot lose your journal."));
        } else {
            for (Path file : backups.subList(0, Math.min(8, backups.size()))) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(file.getFileName().toString());
                name.getStyleClass().add("row-title");
                Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                        modifiedText(file),
                        BackupService.readableSize(sizeOf(file)),
                        file.getFileName().toString().startsWith("automatic-")
                                ? "created automatically" : "created by you"));
                meta.getStyleClass().add("meta");
                row.getChildren().addAll(name, meta, Ui.spacer(),
                        Ui.linkButton("Reveal", () -> reveal(file)));
                list.getChildren().add(row);
            }
        }
        box.getChildren().add(Ui.cardWithHeader("Recent backups", null, list));
        return Ui.card("Backup", box);
    }

    private void createBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save a backup");
        chooser.setInitialFileName(BackupService.suggestedFile().getFileName().toString());
        chooser.setInitialDirectory(AppPaths.backupsDir().toFile());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KinderJournal backup", "*" + BackupService.EXTENSION));
        java.io.File chosen = chooser.showSaveDialog(shell.window());
        if (chosen == null) {
            return;
        }
        runBackup(chosen.toPath());
    }

    private void quickBackup() {
        Path target = AppPaths.backupsDir().resolve(
                "manual-" + java.time.LocalDate.now() + "-"
                        + (System.currentTimeMillis() % 100000) + BackupService.EXTENSION);
        runBackup(target);
    }

    private void runBackup(Path target) {
        try {
            BackupService service = new BackupService(context.repo().db());
            BackupService.BackupResult result = service.backupTo(target);
            Ui.info("Backup created",
                    "Saved to:\n" + result.file().toAbsolutePath()
                            + "\n\nContains: " + result.summary()
                            + "\n\nKeep this file somewhere other than this computer, for example "
                            + "on a USB stick or your school's secure drive.");
            refresh();
        } catch (IOException | RuntimeException ex) {
            Ui.error("The backup could not be created", ex);
        }
    }

    private void openBackupsFolder() {
        Path dir = AppPaths.backupsDir();
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (IOException | UnsupportedOperationException ex) {
            Ui.logError("Opening the backups folder", ex);
        }
        Ui.info("Backups folder", dir.toAbsolutePath().toString());
    }

    private void reveal(Path file) {
        Ui.info("Backup file", file.toAbsolutePath().toString());
    }

    // ----------------------------------------------------------------- restore

    private Node buildRestoreCard() {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.meta("Restoring replaces everything currently in KinderJournal - "
                + "classes, children, attendance, observations, interviews and photos - with the "
                + "contents of the backup file. A safety copy of your current data is taken first, "
                + "so a mistake can be undone."));

        HBox actions = new HBox(8, Ui.dangerButton("Restore from a backup\u2026", this::restore));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);
        return Ui.card("Restore", box);
    }

    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a backup to restore");
        chooser.setInitialDirectory(AppPaths.backupsDir().toFile());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KinderJournal backup", "*" + BackupService.EXTENSION));
        java.io.File chosen = chooser.showOpenDialog(shell.window());
        if (chosen == null) {
            return;
        }
        Path source = chosen.toPath();
        if (!BackupService.looksLikeBackup(source)) {
            Ui.error("That is not a backup file",
                    "KinderJournal backups end in " + BackupService.EXTENSION
                            + " and are created from this screen. Nothing was changed.");
            return;
        }
        if (!Ui.confirmDestructive("Restore from this backup?",
                "File: " + source.getFileName() + "\n\n"
                        + "Everything currently in KinderJournal will be replaced. A safety copy of "
                        + "your current data will be saved in the backups folder first.\n\n"
                        + "If you are unsure, cancel and create a fresh backup now.",
                "Restore now")) {
            return;
        }
        try {
            BackupService service = new BackupService(context.repo().db());
            BackupService.RestoreResult result = service.restoreFrom(source);
            context.reloadCurrentGroup();
            context.setCurrentChild(null);
            context.notifyDataChanged();
            Ui.info("Restore complete",
                    "Your data has been replaced with the contents of the backup ("
                            + result.photos() + (result.photos() == 1 ? " photo" : " photos")
                            + ", database " + result.databaseIntegrity() + ").\n\n"
                            + "A safety copy of what was here before is saved as:\n"
                            + result.safetyCopy().toAbsolutePath());
            refresh();
        } catch (IOException | RuntimeException ex) {
            Ui.error("The restore could not be completed", ex);
        }
    }

    // ------------------------------------------------------------------ health

    private Node buildHealthCard() {
        VBox box = new VBox(10);

        String integrity;
        try {
            integrity = context.repo().db().integrityCheck();
        } catch (RuntimeException ex) {
            integrity = "could not be checked: " + ex.getMessage();
        }
        box.getChildren().add(Ui.keyValue("Database consistency", integrity));
        box.getChildren().add(Ui.keyValue("Schema version",
                String.valueOf(app.kinderjournal.db.Db.SCHEMA_VERSION)));

        List<Photo> missing = context.repo().photos().listMissingFiles();
        if (missing.isEmpty()) {
            box.getChildren().add(Ui.keyValue("Photo files", "all present"));
        } else {
            HBox banner = new HBox(8);
            banner.getStyleClass().add("warning-banner");
            Label label = new Label(Text.plural(missing.size(), "attached photo has",
                    "attached photos have") + " no image file in the photos folder. They may have "
                    + "been moved or deleted outside the app. Open the record each one belongs to "
                    + "and re-attach a replacement, or remove the entry.");
            label.setWrapText(true);
            banner.getChildren().add(label);
            box.getChildren().add(banner);

            VBox list = new VBox(4);
            for (Photo photo : missing.subList(0, Math.min(12, missing.size()))) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(photo.getFile());
                name.getStyleClass().add("row-subtitle");
                Label where = new Label("attached to " + photo.getOwnerType().label());
                where.getStyleClass().add("meta");
                row.getChildren().addAll(name, where);
                if (photo.getChildId() > 0) {
                    var child = context.repo().children().find(photo.getChildId());
                    if (child != null) {
                        row.getChildren().addAll(Ui.spacer(), Ui.linkButton("Open profile", () -> {
                            context.setCurrentChild(child);
                            shell.show("children");
                        }));
                    }
                }
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        HBox actions = new HBox(8,
                Ui.button("Run the checks again", this::refresh),
                Ui.button("Open the error log", this::openLog));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);

        box.getChildren().add(Ui.meta("KinderJournal saves each change as it is made, so closing the "
                + "app unexpectedly cannot lose the last record you entered."));
        return Ui.card("Health check", box);
    }

    private void openLog() {
        Path log = AppPaths.logFile();
        if (!Files.isRegularFile(log)) {
            Ui.info("No problems logged", "KinderJournal has not recorded any errors on this "
                    + "computer. The log file will appear here if something goes wrong:\n\n"
                    + log.toAbsolutePath());
            return;
        }
        Ui.info("Error log", log.toAbsolutePath().toString());
    }

    // -------------------------------------------------------- personalisation

    private Node buildPersonalisationCard() {
        VBox box = new VBox(10);
        TextField name = Ui.textField(context.teacherName(),
                "Your name, shown on printed reports", 28);
        box.getChildren().add(Ui.field("Teacher name", name));
        box.getChildren().add(Ui.button("Save", () -> {
            context.setTeacherName(name.getText());
            Ui.info("Saved", "Your name will appear on printed reports and in the sidebar.");
            refresh();
        }));
        box.getChildren().add(Ui.meta("KinderJournal does not have accounts or passwords. If other "
                + "people can use this computer, protect the data folder with a Windows user account "
                + "or a password-protected drive, because the records contain children's personal "
                + "information."));
        return Ui.card("Personalisation and privacy", box);
    }

    // ----------------------------------------------------------------- helpers

    private String modifiedText(Path file) {
        try {
            return STAMP.format(Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis())
                    .atZone(ZoneId.systemDefault()));
        } catch (IOException ex) {
            return "";
        }
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return 0;
        }
    }
}
