package app.kinderjournal.ui;

import app.kinderjournal.db.DataException;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.Child;
import app.kinderjournal.util.AppPaths;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Building blocks shared by every screen: cards, headings, chips, avatars and
 * the standard dialogs.
 *
 * <p>Keeping them here is what makes the interface feel consistent - a card looks
 * the same on the dashboard, in a child's history and in the report builder.
 */
public final class Ui {

    /** Shown in the title bar and report headers. */
    public static final String APP_NAME = "KinderJournal";

    private static final double AVATAR_RADIUS = 20;
    private static final String[] AVATAR_CLASSES = {"av-0", "av-1", "av-2", "av-3", "av-4", "av-5"};

    private Ui() {
    }

    // ------------------------------------------------------------------ layout

    /** A titled white card with a body. */
    public static VBox card(String title, Node... body) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        if (title != null && !title.isBlank()) {
            box.getChildren().add(sectionTitle(title));
        }
        box.getChildren().addAll(body);
        return box;
    }

    /** A card with a title on the left and an action (or count) on the right. */
    public static VBox cardWithHeader(String title, Node trailing, Node... body) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = sectionTitle(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().add(titleLabel);
        header.getChildren().add(spacer);
        if (trailing != null) {
            header.getChildren().add(trailing);
        }
        box.getChildren().add(header);
        box.getChildren().addAll(body);
        return box;
    }

    public static Label screenTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("screen-title");
        return label;
    }

    public static Label screenSubtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("screen-subtitle");
        label.setWrapText(true);
        return label;
    }

    public static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    /** A dimmed caption line, e.g. a record's date or a child's class. */
    public static Label meta(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("meta");
        return label;
    }

    /** Wrapping body text. */
    public static Label body(String text, boolean wrap) {
        Label label = new Label(text);
        label.setWrapText(wrap);
        label.getStyleClass().add("body-text");
        return label;
    }

    /** A small labelled value pair, used in profile and detail panes. */
    public static VBox labelledValue(String label, String value) {
        VBox box = new VBox(2);
        box.getChildren().add(fieldLabel(label));
        Label valueLabel = new Label(value == null || value.isBlank() ? "\u2013" : value);
        valueLabel.setWrapText(true);
        valueLabel.getStyleClass().add(value == null || value.isBlank() ? "field-empty" : "field-value");
        box.getChildren().add(valueLabel);
        return box;
    }

    /** Horizontal row of field boxes that wraps to the next line when narrow. */
    public static javafx.scene.layout.FlowPane fieldRow(Node... fields) {
        javafx.scene.layout.FlowPane pane = new javafx.scene.layout.FlowPane(16, 10);
        pane.getChildren().addAll(fields);
        return pane;
    }

    /** A coloured chip, e.g. an observation category or plan status. */
    public static Label chip(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("chip");
        return label;
    }

    /** A chip tinted with a specific hex colour (used for observation categories). */
    public static Label chipColoured(String text, String hexColour) {
        Label label = chip(text);
        label.setStyle("-fx-background-color: " + tint(hexColour, 0.88)
                + "; -fx-text-fill: " + shade(hexColour, 0.35) + ";");
        return label;
    }

    public static Label pill(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("pill", styleClass);
        return label;
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static Region vspacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    public static HBox row(Node... children) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(children);
        return box;
    }

    /** Label on the left, value right-aligned - used in summary lists. */
    public static HBox keyValue(String key, String value) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("kv-key");
        Region spacer = spacer();
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("kv-value");
        box.getChildren().addAll(keyLabel, spacer, valueLabel);
        return box;
    }

    /** A label + control stacked vertically, the standard form field. */
    public static VBox field(String label, Node control) {
        VBox box = new VBox(4);
        box.getChildren().add(fieldLabel(label));
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        box.getChildren().add(control);
        return box;
    }

    /** A grid of form fields that keeps a comfortable gap between rows. */
    public static javafx.scene.layout.GridPane formGrid() {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.setPadding(new Insets(0));
        return grid;
    }

    // ------------------------------------------------------------------ buttons

    public static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("btn");
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        return button;
    }

    public static Button primaryButton(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("btn-primary");
        return button;
    }

    public static Button dangerButton(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("btn-danger");
        return button;
    }

    /** A borderless button used for inline actions inside list rows. */
    public static Button linkButton(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("btn-link");
        return button;
    }

    public static Button iconButton(String glyph, String tooltip, Runnable action) {
        Button button = new Button(glyph);
        button.getStyleClass().add("btn-icon");
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        if (tooltip != null) {
            button.setTooltip(new Tooltip(tooltip));
        }
        return button;
    }

    /** Adds a style class without losing the ones already present. */
    public static <T extends Node> T withClass(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }

    // ------------------------------------------------------------------ avatars

    /** A circular avatar: the child's photo when present, else their initials. */
    public static StackPane avatar(Child child, double radius) {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("avatar");
        pane.setMinSize(radius * 2, radius * 2);
        pane.setPrefSize(radius * 2, radius * 2);
        pane.setMaxSize(radius * 2, radius * 2);

        Node content = null;
        if (child != null && child.getPhotoFile() != null && AppPaths.photoExists(child.getPhotoFile())) {
            try {
                // Loaded at the frame size, then cropped to fill it: a portrait or
                // landscape snapshot must fill the circle, not shrink within it.
                Image image = new Image(AppPaths.photoFile(child.getPhotoFile()).toUri().toString(),
                        radius * 2, radius * 2, false, true, true);
                if (!image.isError()) {
                    content = cover(image, radius * 2, radius * 2);
                }
            } catch (RuntimeException ignored) {
                // Fall through to the initials placeholder.
            }
        }
        if (content == null) {
            Label initials = new Label(child == null ? "?" : child.initials());
            initials.getStyleClass().addAll("avatar-initials",
                    AVATAR_CLASSES[app.kinderjournal.util.Text.colourIndex(
                            child == null ? "?" : child.fullName(), AVATAR_CLASSES.length)]);
            content = initials;
        }
        // The clip is a piece of geometry, not a layout child, so it is NOT centred
        // by the StackPane: a bare Circle(radius) sits with its centre on the pane's
        // top-left corner, and the circle is then anchored at (-radius, -radius).
        // That left only the bottom-right quarter-disc visible, which is why the
        // avatar looked like a small wedge in the corner of the frame. Move it to
        // the middle of the pane.
        Circle clip = new Circle(radius, radius, radius);
        pane.setClip(clip);
        pane.getChildren().add(content);
        pane.getStyleClass().add("avatar-clip");
        return pane;
    }

    /** A placeholder avatar from plain text (used when no Child is at hand). */
    public static StackPane avatar(String name, double radius) {
        Child stub = new Child();
        String[] parts = app.kinderjournal.util.Text.clean(name).split("\\s+", 2);
        stub.setFirstName(parts.length > 0 ? parts[0] : "");
        stub.setLastName(parts.length > 1 ? parts[1] : "");
        return avatar(stub, radius);
    }

    /** The default avatar size used in lists. */
    public static StackPane avatar(Child child) {
        return avatar(child, AVATAR_RADIUS);
    }

    // ------------------------------------------------------------------ images

    /** Loads an image for display, or null when the file is missing or unreadable. */
    public static Image loadImage(String storedFile, double width) {
        if (storedFile == null || !AppPaths.photoExists(storedFile)) {
            return null;
        }
        try {
            Image image = new Image(AppPaths.photoFile(storedFile).toUri().toString(),
                    width, 0, true, true, true);
            return image.isError() ? null : image;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** An ImageView sized to fit, or a "photo missing" placeholder label. */
    public static Node imageOrPlaceholder(String storedFile, double width, double height) {
        Image image = loadImage(storedFile, Math.max(width, height));
        if (image == null) {
            Label missing = new Label(Messages.get("reports.photoMissing"));
            missing.getStyleClass().add("photo-missing");
            missing.setPrefSize(width, height);
            missing.setAlignment(Pos.CENTER);
            return missing;
        }
        return cover(image, width, height);
    }

    /**
     * Fills a box with an image, cropping the overhang, rather than fitting the
     * whole image inside it.
     *
     * <p>Scaling a picture down until it fits leaves empty bands: a 30x20 sample
     * placed in a square frame became 30x30, so it occupied less than half the
     * width and the frame looked broken rather than letterboxed. Cropping to fill
     * is what a photo frame is expected to do, and it is what the circular child
     * avatar needs - a face should fill the circle, not shrink inside it.
     */
    public static ImageView cover(Image image, double width, double height) {
        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        // The ratio is preserved by the crop below, so the view must not add its
        // own letterboxing on top of it.
        view.setPreserveRatio(false);
        view.setSmooth(true);

        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) {
            return view;
        }
        // The largest centred region of the source with the frame's proportions.
        double targetRatio = width / height;
        double sourceRatio = sourceWidth / sourceHeight;
        double cropWidth = sourceWidth;
        double cropHeight = sourceHeight;
        if (sourceRatio > targetRatio) {
            cropWidth = sourceHeight * targetRatio;
        } else {
            cropHeight = sourceWidth / targetRatio;
        }
        view.setViewport(new Rectangle2D(
                (sourceWidth - cropWidth) / 2, (sourceHeight - cropHeight) / 2,
                cropWidth, cropHeight));
        return view;
    }

    // ------------------------------------------------------------------ dialogs

    /** An information dialog. */
    public static void info(String title, String message) {
        alert(Alert.AlertType.INFORMATION, title, message);
    }

    /** A warning dialog. */
    public static void warn(String title, String message) {
        alert(Alert.AlertType.WARNING, title, message);
    }

    /** Reports a failed save so the teacher is never left guessing. */
    public static void error(String title, String message) {
        alert(Alert.AlertType.ERROR, title, message);
    }

    /**
     * Reports an exception in plain language and writes the detail to the log.
     * Used by every screen's save path so failures are visible, not silent.
     */
    public static void error(String title, Throwable ex) {
        logError(title, ex);
        String detail = ex instanceof DataException
                ? ex.getMessage()
                : (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        error(title, detail + "\n\n" + Messages.get("error.technicalDetails")
                + "\n" + AppPaths.logFile());
    }

    /** Appends a failure to the log file. Never throws. */
    public static void logError(String context, Throwable ex) {
        try {
            String text = "[" + java.time.LocalDateTime.now() + "] " + context + ": " + ex + "\n";
            for (StackTraceElement el : ex.getStackTrace()) {
                text += "    at " + el + "\n";
            }
            Files.writeString(AppPaths.logFile(), text,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never take the app down.
        }
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (alert.getDialogPane() != null) {
            alert.getDialogPane().setMinWidth(460);
            alert.getDialogPane().getStylesheets().addAll(Styles.stylesheet());
            alert.getButtonTypes().setAll(dialogButton("common.close", ButtonBar.ButtonData.OK_DONE));
        }
        alert.showAndWait();
    }

    /** A yes/no confirmation with custom button captions. Returns true when confirmed. */
    public static boolean confirm(String title, String message, String confirmText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(title);
        alert.setContentText(message);
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = dialogButton("common.cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(no, yes);
        alert.getDialogPane().setMinWidth(460);
        alert.getDialogPane().getStylesheets().addAll(Styles.stylesheet());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    /** A destructive confirmation; the confirm button is labelled in red. */
    public static boolean confirmDestructive(String title, String message, String confirmText) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(title);
        alert.setContentText(message);
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = dialogButton("common.cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(no, yes);
        alert.getDialogPane().setMinWidth(500);
        alert.getDialogPane().getStylesheets().addAll(Styles.stylesheet());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    /** Prompts for a single line of text. Returns empty when cancelled. */
    public static Optional<String> prompt(String title, String label, String initialValue) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(APP_NAME);
        dialog.setHeaderText(title);
        TextField field = new TextField(initialValue == null ? "" : initialValue);
        field.setPrefColumnCount(28);
        VBox box = new VBox(8, fieldLabel(label), field);
        box.setPadding(new Insets(4, 4, 4, 4));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setMinWidth(420);
        ButtonType accept = dialogButton("common.save", ButtonBar.ButtonData.OK_DONE);
        ButtonType dismiss = dialogButton("common.cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(accept, dismiss);
        dialog.getDialogPane().getStylesheets().addAll(Styles.stylesheet());
        dialog.setResultConverter(button -> button == accept ? field.getText() : null);
        javafx.application.Platform.runLater(field::requestFocus);
        return dialog.showAndWait();
    }

    /**
     * A dialog button captioned in the current language. JavaFX's own button types
     * are always English, and their captions cannot be replaced once built, so the
     * localized text is carried by a type of our own.
     */
    private static ButtonType dialogButton(String key, ButtonBar.ButtonData data) {
        return new ButtonType(Messages.get(key), data);
    }

    // ------------------------------------------------------------------ dates

    /**
     * A {@link DatePicker} that understands the formats teachers actually type and
     * falls back to the previous value rather than clearing the field on a typo.
     */
    public static DatePicker datePicker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setConverter(new StringConverter<>() {
            // Month names follow the selected language, like every other date shown.
            private final DateTimeFormatter format =
                    DateTimeFormatter.ofPattern("d MMM yyyy", Messages.current().locale());

            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : format.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                LocalDate parsed = app.kinderjournal.util.Dates.parseLoose(text);
                return parsed == null ? picker.getValue() : parsed;
            }
        });
        picker.setPrefWidth(170);
        picker.setEditable(true);
        return picker;
    }

    /** A read-only text area styled for long-form note entry. */
    public static TextArea textArea(String value, int rows) {
        TextArea area = new TextArea(value == null ? "" : value);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        area.getStyleClass().add("text-area");
        return area;
    }

    /** A text field with a prompt, sized to the column count. */
    public static TextField textField(String value, String prompt, int columns) {
        TextField field = new TextField(value == null ? "" : value);
        field.setPromptText(prompt);
        field.setPrefColumnCount(columns);
        return field;
    }

    /** A read-only, selectable text block that keeps line breaks - for notes. */
    public static TextFlow textBlock(String text) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("text-block");
        String value = text == null ? "" : text;
        for (String line : value.split("\n", -1)) {
            javafx.scene.text.Text node = new javafx.scene.text.Text(line);
            flow.getChildren().add(node);
            flow.getChildren().add(new javafx.scene.text.Text("\n"));
        }
        return flow;
    }

    /** A scrollable column, the standard screen body. */
    public static javafx.scene.control.ScrollPane scroll(Node content) {
        javafx.scene.control.ScrollPane pane = new javafx.scene.control.ScrollPane(content);
        pane.setFitToWidth(true);
        // A screen whose content has a minimum width - the report builder's two
        // columns, for instance - would otherwise be squeezed until its right-hand
        // labels fell outside the window. Allowing the bar to appear means narrow
        // windows can still reach everything.
        pane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.getStyleClass().add("screen-scroll");
        return pane;
    }

    /** A vertical stack with the standard screen padding. */
    public static VBox page(Node... children) {
        VBox box = new VBox(16);
        box.setPadding(new Insets(20, 24, 28, 24));
        box.getChildren().addAll(children);
        return box;
    }

    /** An empty-state block: a headline plus guidance on what to do next. */
    public static VBox emptyState(String headline, String detail, Node action) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28, 20, 28, 20));
        box.getStyleClass().add("empty-state");
        Label title = new Label(headline);
        title.getStyleClass().add("empty-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("empty-detail");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(460);
        detailLabel.setAlignment(Pos.CENTER);
        detailLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        box.getChildren().addAll(title, detailLabel);
        if (action != null) {
            box.getChildren().add(action);
        }
        return box;
    }

    /** A coloured legend dot with a label. */
    public static HBox legendDot(String hexColour, String label) {
        Circle dot = new Circle(5, javafx.scene.paint.Color.web(hexColour));
        Label text = new Label(label);
        text.getStyleClass().add("legend-label");
        HBox box = new HBox(6, dot, text);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Ensures a folder exists, returning it; throws IOException when it cannot. */
    public static Path ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    /** Convenience: a comma-joined list of labels for the given ids. */
    public static String joinLabels(List<String> labels) {
        return String.join(", ", labels);
    }

    // ------------------------------------------------------------------ colours

    /** A light tint of a hex colour, for chip backgrounds. */
    public static String tint(String hex, double amount) {
        return mix(hex, "#ffffff", amount);
    }

    /** A darker shade of a hex colour, for chip text. */
    public static String shade(String hex, double amount) {
        return mix(hex, "#000000", amount);
    }

    private static String mix(String hex, String other, double amount) {
        try {
            int r1 = Integer.parseInt(hex.substring(1, 3), 16);
            int g1 = Integer.parseInt(hex.substring(3, 5), 16);
            int b1 = Integer.parseInt(hex.substring(5, 7), 16);
            int r2 = Integer.parseInt(other.substring(1, 3), 16);
            int g2 = Integer.parseInt(other.substring(3, 5), 16);
            int b2 = Integer.parseInt(other.substring(5, 7), 16);
            int r = (int) Math.round(r1 + (r2 - r1) * amount);
            int g = (int) Math.round(g1 + (g2 - g1) * amount);
            int b = (int) Math.round(b1 + (b2 - b1) * amount);
            return String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
        } catch (RuntimeException ex) {
            return hex;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
