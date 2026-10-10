package app.kinderjournal.ui.screen;

import app.kinderjournal.backup.BackupService;
import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.Photo;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

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
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Messages.current().locale());

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
        return Messages.get("data.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("data.subtitle");
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
        page.getChildren().add(buildLanguageCard());
        root.setContent(page);
    }

    // ----------------------------------------------------------------- storage

    private Node buildWhereCard() {
        Map<String, Integer> counts = context.repo().counts();
        VBox details = new VBox(6);

        TextField path = new TextField(context.repo().dataLocationText());
        path.setEditable(false);
        path.setPrefColumnCount(52);

        HBox pathRow = new HBox(8, path, Ui.button(Messages.get("data.openFolder"),
                this::openDataFolder));
        pathRow.setAlignment(Pos.CENTER_LEFT);

        details.getChildren().addAll(
                Ui.fieldLabel(Messages.get("data.folder")),
                pathRow,
                Ui.meta(Messages.get("data.folderSummary",
                        AppPaths.databaseFile().getFileName(),
                        AppPaths.photosDir().getFileName(),
                        BackupService.readableSize(BackupService.dataFolderSize()))));

        VBox counts_ = new VBox(4);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            counts_.getChildren().add(Ui.keyValue(entry.getKey(), String.valueOf(entry.getValue())));
        }
        details.getChildren().add(counts_);

        details.getChildren().add(Ui.meta(Messages.get("data.noNetwork")));
        return Ui.card(Messages.get("data.where"), details);
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
        Ui.info(Messages.get("data.folder"), dir.toAbsolutePath().toString());
    }

    // ------------------------------------------------------------------ backup

    private Node buildBackupCard() {
        VBox box = new VBox(10);

        box.getChildren().add(Ui.meta(Messages.get("data.backupExplain")));

        HBox actions = new HBox(8,
                Ui.primaryButton(Messages.get("data.createBackup"), this::createBackup),
                Ui.button(Messages.get("data.quickBackup"), this::quickBackup),
                Ui.button(Messages.get("data.openBackups"), this::openBackupsFolder));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);

        List<Path> backups = BackupService.listBackups();
        VBox list = new VBox(6);
        if (backups.isEmpty()) {
            list.getChildren().add(Ui.meta(Messages.get("data.noBackups")));
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
                                ? Messages.get("data.createdAutomatically")
                                : Messages.get("data.createdByYou")));
                meta.getStyleClass().add("meta");
                row.getChildren().addAll(name, meta, Ui.spacer(),
                        Ui.linkButton(Messages.get("data.reveal"), () -> reveal(file)));
                list.getChildren().add(row);
            }
        }
        box.getChildren().add(Ui.cardWithHeader(Messages.get("data.recentBackups"), null, list));
        return Ui.card(Messages.get("data.backup"), box);
    }

    private void createBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("data.saveBackup"));
        chooser.setInitialFileName(BackupService.suggestedFile().getFileName().toString());
        chooser.setInitialDirectory(AppPaths.backupsDir().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("data.backupFilter"), "*" + BackupService.EXTENSION));
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
            String body = Messages.get("data.backupCreatedBody") + "\n"
                    + result.file().toAbsolutePath() + "\n\n"
                    + Messages.get("data.backupCreatedContains", result.summary()) + "\n\n"
                    + Messages.get("data.backupCreatedKeep");
            Ui.info(Messages.get("data.backupCreated"), body);
            refresh();
        } catch (IOException | RuntimeException ex) {
            Ui.error(Messages.get("data.backupFailed"), ex);
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
        Ui.info(Messages.get("data.openBackups"), dir.toAbsolutePath().toString());
    }

    private void reveal(Path file) {
        Ui.info(Messages.get("data.backupFile"), file.toAbsolutePath().toString());
    }

    // ----------------------------------------------------------------- restore

    private Node buildRestoreCard() {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.meta(Messages.get("data.restoreExplain")));

        HBox actions = new HBox(8,
                Ui.dangerButton(Messages.get("data.restoreAction"), this::restore));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);
        return Ui.card(Messages.get("data.restore"), box);
    }

    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("data.chooseBackup"));
        chooser.setInitialDirectory(AppPaths.backupsDir().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("data.backupFilter"), "*" + BackupService.EXTENSION));
        java.io.File chosen = chooser.showOpenDialog(shell.window());
        if (chosen == null) {
            return;
        }
        Path source = chosen.toPath();
        if (!BackupService.looksLikeBackup(source)) {
            Ui.error(Messages.get("data.notABackup"),
                    Messages.get("data.notABackupBody", BackupService.EXTENSION));
            return;
        }
        if (!Ui.confirmDestructive(Messages.get("data.restoreTitle"),
                Messages.get("data.restoreBody", source.toAbsolutePath()) + "\n\n"
                        + Messages.get("data.restoreBody2") + "\n\n"
                        + Messages.get("data.restoreBody3"),
                Messages.get("data.restoreConfirm"))) {
            return;
        }
        try {
            BackupService service = new BackupService(context.repo().db());
            BackupService.RestoreResult result = service.restoreFrom(source);
            context.reloadCurrentGroup();
            context.setCurrentChild(null);
            context.notifyDataChanged();
            String photos = result.photos() == 1 ? Messages.get("count.photo", result.photos())
                    : Messages.get("count.photos", result.photos());
            Ui.info(Messages.get("data.restoreDone"),
                    Messages.get("data.restoreDoneBody", photos, result.databaseIntegrity())
                            + "\n\n" + Messages.get("data.restoreSafety") + "\n"
                            + result.safetyCopy().toAbsolutePath());
            refresh();
        } catch (IOException | RuntimeException ex) {
            Ui.error(Messages.get("data.restoreFailed"), ex);
        }
    }

    // ------------------------------------------------------------------ health

    private Node buildHealthCard() {
        VBox box = new VBox(10);

        String integrity;
        try {
            integrity = context.repo().db().integrityCheck();
        } catch (RuntimeException ex) {
            integrity = Messages.get("data.integrityFailed", ex.getMessage());
        }
        box.getChildren().add(Ui.keyValue(Messages.get("data.integrity"), integrity));
        box.getChildren().add(Ui.keyValue(Messages.get("data.schema"),
                String.valueOf(app.kinderjournal.db.Db.SCHEMA_VERSION)));

        List<Photo> missing = context.repo().photos().listMissingFiles();
        if (missing.isEmpty()) {
            box.getChildren().add(Ui.keyValue(Messages.get("data.photoFiles"),
                    Messages.get("data.allPresent")));
        } else {
            HBox banner = new HBox(8);
            banner.getStyleClass().add("warning-banner");
            Label label = new Label(missing.size() == 1
                    ? Messages.get("photos.missingWarningOne")
                    : Messages.get("data.attachedPhotosMissing", missing.size()));
            label.setWrapText(true);
            banner.getChildren().add(label);
            box.getChildren().add(banner);

            VBox list = new VBox(4);
            for (Photo photo : missing.subList(0, Math.min(12, missing.size()))) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(photo.getFile());
                name.getStyleClass().add("row-subtitle");
                Label where = new Label(Messages.get("data.attachedTo",
                        photo.getOwnerType().label()));
                where.getStyleClass().add("meta");
                row.getChildren().addAll(name, where);
                if (photo.getChildId() > 0) {
                    var child = context.repo().children().find(photo.getChildId());
                    if (child != null) {
                        row.getChildren().addAll(Ui.spacer(),
                                Ui.linkButton(Messages.get("button.openProfile"), () -> {
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
                Ui.button(Messages.get("data.runChecks"), this::refresh),
                Ui.button(Messages.get("data.openLog"), this::openLog));
        actions.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(actions);

        box.getChildren().add(Ui.meta(Messages.get("data.saveEachChange")));
        return Ui.card(Messages.get("data.health"), box);
    }

    private void openLog() {
        Path log = AppPaths.logFile();
        if (!Files.isRegularFile(log)) {
            Ui.info(Messages.get("data.noLog"),
                    Messages.get("data.noLogBody") + "\n\n" + log.toAbsolutePath());
            return;
        }
        Ui.info(Messages.get("data.errorLog"), log.toAbsolutePath().toString());
    }

    // -------------------------------------------------------- personalisation

    private Node buildPersonalisationCard() {
        VBox box = new VBox(10);
        TextField name = Ui.textField(context.teacherName(),
                Messages.get("data.teacherNameHint"), 28);
        box.getChildren().add(Ui.field(Messages.get("data.teacherName"), name));
        box.getChildren().add(Ui.button(Messages.get("common.save"), () -> {
            context.setTeacherName(name.getText());
            Ui.info(Messages.get("data.teacherNameSaved"),
                    Messages.get("data.teacherNameSavedBody"));
            refresh();
        }));
        box.getChildren().add(Ui.meta(Messages.get("data.privacyNote")));
        return Ui.card(Messages.get("data.personalisation"), box);
    }

    // ----------------------------------------------------------------- language

    /**
     * The language section: the same choice as the status-bar toggle, with a note
     * of how complete the Mongolian translation is.
     *
     * <p>Switching here goes through {@link MainShell}'s listener, so the whole
     * window is rebuilt in the new language rather than patched in place.
     */
    private Node buildLanguageCard() {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.meta(Messages.get("data.languageExplain")));

        ComboBox<Lang> choice = new ComboBox<>();
        choice.getItems().addAll(Lang.values());
        choice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Lang value) {
                return value == null ? "" : value.nativeName();
            }

            @Override
            public Lang fromString(String text) {
                return null;
            }
        });
        choice.setValue(Messages.current());
        choice.setPrefWidth(220);
        choice.valueProperty().addListener((observable, previous, next) -> {
            if (next == null || next == Messages.current()) {
                return;
            }
            context.repo().db().saveSetting("language", next.code());
            Messages.setCurrent(next);
        });

        box.getChildren().add(Ui.field(Messages.get("data.language"), choice));
        box.getChildren().add(Ui.meta(Messages.get("data.languageMongolian",
                translationPercent())));
        return Ui.card(Messages.get("data.language"), box);
    }

    /** How much of the English catalogue the Mongolian one translates, as a percentage. */
    private static String translationPercent() {
        int total = Messages.keys().size();
        if (total == 0) {
            return "0%";
        }
        int missing = Messages.missingFor(Lang.MONGOLIAN).size();
        return Math.round((total - missing) * 100.0 / total) + "%";
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
