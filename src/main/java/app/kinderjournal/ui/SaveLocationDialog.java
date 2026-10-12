package app.kinderjournal.ui;

import app.kinderjournal.i18n.Messages;
import app.kinderjournal.report.DocumentFormat;
import app.kinderjournal.util.AppPaths;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A save dialog built from ordinary controls, used instead of the Windows one.
 *
 * <p>JavaFX's {@code FileChooser} is a thin wrapper around the Windows common
 * dialog - {@code com.sun.glass.ui.win.WinCommonDialogs} - and on some computers
 * that dialog refuses to save anywhere: it reports "you don't have permission to
 * save in this location" for every folder, including ones the program itself can
 * write to without trouble. Nothing the program does affects that, because the
 * refusal comes from the dialog rather than from the file system.
 *
 * <p>So this dialog does the choosing itself: a folder box that can be typed into
 * or pasted, buttons for the places a teacher actually uses, a list of this
 * computer's drives, and the file name. Because the program then writes the file
 * with its own code, the only thing that can stop a save is a genuine refusal from
 * Windows - and that is reported in full rather than as a vague message.
 */
public final class SaveLocationDialog {

    /** Where the chosen destination is remembered between reports. */
    private static final String LAST_FOLDER = "last_export_folder";

    private final DocumentFormat format;
    private final String suggestedName;
    private final AppContext context;

    private final TextField folderField = new TextField();
    private final TextField nameField = new TextField();
    private final Label message = new Label();
    private final ListView<String> places = new ListView<>();

    private Path result;
    private Stage stage;

    SaveLocationDialog(DocumentFormat format, String suggestedName, AppContext context) {
        this.format = format;
        this.suggestedName = suggestedName;
        this.context = context;
    }

    /**
     * Shows the dialog and waits for the teacher to choose.
     *
     * @return the file to save to, or null if they cancelled
     */
    public static Path choose(Window owner, DocumentFormat format, String suggestedName,
                              AppContext context) {
        SaveLocationDialog dialog = new SaveLocationDialog(format, suggestedName, context);
        dialog.show(owner);
        return dialog.result;
    }

    // -------------------------------------------------------------------- build

    /**
     * Builds the dialog's contents.
     *
     * <p>Kept apart from showing it so the interface tests can lay it out and look at
     * it, which is not possible for a window that is waiting to be clicked.
     */
    public VBox buildBody() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("save-dialog");

        Label heading = new Label(Messages.get("format.saveTitle"));
        heading.getStyleClass().add("screen-title");

        folderField.setPrefColumnCount(46);
        folderField.setText(startFolder().toString());
        folderField.textProperty().addListener((observable, old, now) -> checkFolder());

        nameField.setPrefColumnCount(30);
        nameField.setText(suggestedName);

        Button save = Ui.primaryButton(Messages.get("format.save"), this::commit);
        Button cancel = Ui.button(Messages.get("common.cancel"), this::close);
        Button browse = Ui.button(Messages.get("format.browse"), this::browse);

        places.setPrefHeight(120);
        places.getItems().setAll(placeNames());
        places.getSelectionModel().selectedIndexProperty().addListener(
                (observable, old, now) -> {
                    int index = now.intValue();
                    if (index >= 0 && index < placePaths().size()) {
                        folderField.setText(placePaths().get(index).toString());
                    }
                });

        message.setWrapText(true);
        message.setMinWidth(0);
        message.getStyleClass().add("meta");
        message.setMaxWidth(520);

        HBox folderRow = new HBox(8, folderField, browse);
        HBox.setHgrow(folderField, Priority.ALWAYS);
        HBox nameRow = new HBox(8, new Label(Messages.get("format.fileName")), nameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(10, save, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(
                heading,
                new Label(Messages.get("format.saveWhere")),
                folderRow,
                new Label(Messages.get("format.commonPlaces")),
                places,
                nameRow,
                message,
                buttons);
        // Work the status out now, so the dialog reads correctly the moment it is
        // built rather than only once it is shown.
        checkFolder();
        if (message.getText() == null || message.getText().isBlank()) {
            // Never leave the line empty: a blank status line looks like a fault.
            message.setText(Messages.get("format.enterFolder"));
        }
        return box;
    }

    private void show(Window owner) {
        VBox box = buildBody();
        Scene scene = new Scene(box);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
            } else if (event.getCode() == KeyCode.ENTER) {
                commit();
            }
        });

        stage = new Stage();
        stage.setTitle(Messages.get("format.saveTitle"));
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(scene);
        checkFolder();
        stage.showAndWait();
    }

    // ------------------------------------------------------------- the places

    private List<String> placeNames() {
        List<String> names = new ArrayList<>();
        for (Path path : placePaths()) {
            names.add(path.toString());
        }
        return names;
    }

    /**
     * The folders offered for one click: the ones a teacher saves into, then the
     * drives on this computer, so a memory stick or a shared drive can be reached.
     */
    private List<Path> placePaths() {
        List<Path> paths = new ArrayList<>();
        Path home = Path.of(System.getProperty("user.home", "."));
        for (String name : List.of("Documents", "Desktop", "Downloads", "Pictures")) {
            paths.add(home.resolve(name));
        }
        paths.add(AppPaths.dataDir());
        paths.add(AppPaths.backupsDir());

        for (java.io.File root : java.io.File.listRoots()) {
            paths.add(root.toPath());
        }
        return paths.stream().filter(Files::isDirectory).distinct().toList();
    }

    private Path startFolder() {
        String remembered = context.repo().db().setting(LAST_FOLDER, "");
        if (!remembered.isBlank()) {
            Path path = Path.of(remembered);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        Path documents = Path.of(System.getProperty("user.home", "."), "Documents");
        return Files.isDirectory(documents) ? documents : AppPaths.dataDir();
    }

    /** The standard folder chooser, offered for when it works on this computer. */
    private void browse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Messages.get("format.saveWhere"));
        Path current = Path.of(folderField.getText().trim());
        if (Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        java.io.File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            folderField.setText(chosen.getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------ saving

    /** Says whether the folder in the box can be written to, before saving. */
    private void checkFolder() {
        String text = folderField.getText() == null ? "" : folderField.getText().trim();
        boolean problem = describeFolder(text, message);
        message.getStyleClass().removeAll("form-error", "meta");
        message.getStyleClass().add(problem ? "form-error" : "meta");
    }

    /**
     * Works out what to tell the teacher about a folder.
     *
     * <p>Separate from the dialog so it can be tested without showing one. Returns
     * true when the folder is a problem, and writes the sentence to explain it.
     */
    /** Public so the interface tests can check the advice without showing a dialog. */
    public static boolean describeFolder(String text, Label target) {
        if (text == null || text.isBlank()) {
            target.setText(Messages.get("format.enterFolder"));
            return true;
        }
        Path folder;
        try {
            folder = Path.of(text.trim());
        } catch (RuntimeException ex) {
            target.setText(Messages.get("format.badFolder", text));
            return true;
        }
        if (Files.isDirectory(folder)) {
            Path blocked = firstUnwritable(folder);
            if (blocked == null) {
                target.setText(Messages.get("format.canSaveHere", folder.toString()));
                return false;
            }
            target.setText(Messages.get("format.cannotWriteHere", blocked.toString()));
            return true;
        }
        if (Files.exists(folder)) {
            target.setText(Messages.get("format.notAFolder", text));
            return true;
        }
        // Not there yet: say it will be created, then let the save try.
        target.setText(Messages.get("format.willCreate", text));
        return false;
    }

    /**
     * Builds a dialog without showing it.
     *
     * <p>Public so the interface tests and the layout tool can lay the dialog out and
     * look at it, which cannot be done to a window that is waiting for a click.
     */
    public static SaveLocationDialog forTesting(DocumentFormat format, String suggestedName,
                                                AppContext context) {
        return new SaveLocationDialog(format, suggestedName, context);
    }

    /** The first folder from the chosen one upwards that this program cannot write to. */
    private static Path firstUnwritable(Path folder) {
        Path current = folder.toAbsolutePath();
        while (current != null && !Files.isDirectory(current)) {
            current = current.getParent();
        }
        return current != null && !Files.isWritable(current) ? current : null;
    }


    /** Checks the folder, then remembers the choice and closes. */
    private void commit() {
        String folderText = folderField.getText() == null ? "" : folderField.getText().trim();
        // describeFolder writes the reason into the label when it refuses.
        if (describeFolder(folderText, message)) {
            message.getStyleClass().removeAll("form-error", "meta");
            message.getStyleClass().add("form-error");
            return;
        }
        Path folder = Path.of(folderText);

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            name = suggestedName;
        }
        // A teacher who leaves the extension off still gets a file Office opens.
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith("." + format.extension())) {
            name = name + "." + format.extension();
        }

        try {
            Files.createDirectories(folder);
        } catch (IOException | RuntimeException ex) {
            // Nothing was written; show exactly why so it can be passed on.
            message.setText(Messages.get("format.couldNotCreate", folder.toString(),
                    describe(ex)));
            message.getStyleClass().removeAll("form-error", "meta");
            message.getStyleClass().add("form-error");
            return;
        }

        Path target = folder.resolve(name);
        Path existing = Files.exists(target) ? target : null;
        if (existing != null && !Ui.confirmDestructive(Messages.get("format.replaceTitle"),
                Messages.get("format.replaceBody", target.toString()),
                Messages.get("format.replaceConfirm"))) {
            return;
        }

        result = target;
        context.repo().db().saveSetting(LAST_FOLDER, folder.toAbsolutePath().toString());
        close();
    }

    /** The useful part of a Windows failure, including its code. */
    private static String describe(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable cause = ex;
        while (cause != null) {
            if (sb.length() > 0) {
                sb.append(" \u2190 ");
            }
            sb.append(cause.getClass().getSimpleName());
            if (cause instanceof java.nio.file.FileSystemException fse
                    && fse.getReason() != null) {
                sb.append(": ").append(fse.getReason().trim());
            } else if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage().trim());
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }

    private void close() {
        if (stage != null) {
            stage.close();
        }
    }
}
