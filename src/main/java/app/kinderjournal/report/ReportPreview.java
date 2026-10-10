package app.kinderjournal.report;

import app.kinderjournal.i18n.Messages;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The print preview window.
 *
 * <p>Shows the generated document as it will appear, and offers the two things a
 * teacher actually needs next: send it to a printer, or keep it as a file. Printing
 * goes through the system print dialog, which also offers "Microsoft Print to PDF",
 * so a report can be saved as a PDF without any extra software being installed.
 */
public final class ReportPreview {

    private ReportPreview() {
    }

    /** Opens the preview for an already-generated document. */
    public static void show(ReportRenderer.Rendered rendered, Window owner, String title) {
        Stage window = new Stage();
        window.initModality(Modality.WINDOW_MODAL);
        window.initOwner(owner);
        window.setTitle(Messages.get("reports.previewTitle", title));

        VBox page = new VBox(10);
        page.setPadding(new Insets(22, 26, 22, 26));
        page.setStyle("-fx-background-color: #ffffff;");
        page.setMaxWidth(840);
        new HtmlPreview().renderInto(page, rendered.html());

        ScrollPane scroller = Ui.scroll(page);
        scroller.setStyle("-fx-background-color: #e9edf1;");

        Label hint = new Label(Messages.get("reports.previewHint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("meta");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(
                Ui.primaryButton(Messages.get("reports.printAction"), () -> print(rendered)),
                Ui.button(Messages.get("reports.saveAsFile"), () -> save(rendered, window)),
                Ui.spacer(),
                Ui.button(Messages.get("common.close"), window::close));

        VBox bottom = new VBox(8, hint, actions);
        bottom.setPadding(new Insets(12, 16, 14, 16));
        bottom.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dfe4e8 transparent "
                + "transparent transparent; -fx-border-width: 1 0 0 0;");

        BorderPane pane = new BorderPane();
        pane.setCenter(scroller);
        pane.setBottom(bottom);

        Scene scene = new Scene(pane, 900, 760);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setFill(Color.web("#e9edf1"));
        window.setScene(scene);
        window.show();
    }

    // ----------------------------------------------------------------- actions

    /** Writes the report to a temporary file and asks the system to print it. */
    private static void print(ReportRenderer.Rendered rendered) {
        try {
            Path file = writeTemporary(rendered);
            openExternally(file);
        } catch (IOException ex) {
            Ui.error(Messages.get("reports.couldNotPrepare"), ex);
        }
    }

    private static void save(ReportRenderer.Rendered rendered, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("reports.saveTitle"));
        chooser.setInitialFileName(fileName(rendered.title()));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Messages.get("reports.saveFilter"), "*.html"));
        java.io.File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) {
            return;
        }
        try {
            Files.writeString(chosen.toPath(), rendered.html());
            if (Ui.confirm(Messages.get("reports.saved"),
                    Messages.get("reports.savedBody") + "\n" + chosen.getAbsolutePath()
                            + "\n\n" + Messages.get("reports.openIt"),
                    Messages.get("reports.openItAction"))) {
                openExternally(chosen.toPath());
            }
        } catch (IOException ex) {
            Ui.error(Messages.get("reports.couldNotSave"), ex);
        }
    }

    /** Opens a file in the system's default application, reporting any failure. */
    public static void openExternally(Path file) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (IOException | UnsupportedOperationException ex) {
            Ui.logError("Opening " + file, ex);
        }
        Ui.info(Messages.get("reports.openManually"),
                Messages.get("reports.openManuallyBody") + "\n\n" + file.toAbsolutePath()
                        + "\n\n" + Messages.get("reports.openManuallyHow"));
    }

    /** Writes a generated report into the app's own reports folder. */
    public static Path writeTemporary(ReportRenderer.Rendered rendered) throws IOException {
        Path dir = AppPaths.dataDir().resolve("reports");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName(rendered.title().replace(".html", ""))
                .replace(".html", "") + "-" + System.currentTimeMillis() % 100000 + ".html");
        Files.writeString(file, rendered.html());
        return file;
    }

    private static String fileName(String title) {
        String base = title.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "report";
        }
        return base.toLowerCase().endsWith(".html") ? base : base + ".html";
    }

    /** The alignment used for the preview's action row. */
    static HBox actionRow(javafx.scene.Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
