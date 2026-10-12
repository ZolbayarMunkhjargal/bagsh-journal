package app.kinderjournal.tools;

import app.kinderjournal.ui.Styles;

import javafx.application.Application;
import javafx.css.Styleable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Prints the text colour JavaFX actually resolves for each control.
 *
 * <p>Written to settle a question a stylesheet alone cannot answer: the theme in
 * use fills some controls from {@code -fx-text-background-color}, a value derived
 * from the background, so a light background can silently produce white text.
 * Run with {@code run.bat --css-probe} (a developer tool, not shipped).
 */
public final class CssProbe extends Application {

    @Override
    public void start(Stage stage) {
        CheckBox check = new CheckBox("Checkbox label");
        RadioButton radio = new RadioButton("Radio label");
        Label label = new Label("Plain label");
        Label onCard = new Label("Label on a card");
        CheckBox checkOnCard = new CheckBox("Checkbox on a card");

        Label fieldLabel = new Label("Field label");
        fieldLabel.getStyleClass().add("field-label");
        Label meta = new Label("Meta text");
        meta.getStyleClass().add("meta");

        VBox card = new VBox(6, onCard, checkOnCard);
        card.getStyleClass().add("card");
        VBox root = new VBox(8, check, radio, label, fieldLabel, meta, card);
        root.getStyleClass().add("root");
        root.setPadding(new javafx.geometry.Insets(16));

        Scene scene = new Scene(root, 420, 320);
        scene.getStylesheets().addAll(Styles.stylesheet());
        root.applyCss();
        root.layout();

        System.out.println("Resolved text colours");
        report("plain Label", label);
        report("CheckBox (Labeled)", check);
        report("RadioButton (Labeled)", radio);
        report("Label on a card", onCard);
        report("CheckBox on a card", checkOnCard);
        report("field-label", fieldLabel);
        report("meta", meta);

        javafx.application.Platform.exit();
        System.exit(0);
    }

    private static void report(String what, Node node) {
        StringBuilder sb = new StringBuilder(what);
        sb.append(" -> ");
        if (node instanceof javafx.scene.control.Labeled labelled) {
            sb.append("Labelled.textFill=").append(colour(labelled.getTextFill()));
        } else {
            sb.append("Labelled.textFill=(n/a)");
        }
        Node text = node.lookup(".text");
        if (text instanceof Text t) {
            sb.append(",  .text.fill=").append(colour(t.getFill()));
        } else {
            sb.append(",  .text.fill=(not found)");
        }
        Styleable styleable = node;
        if (styleable.getStyleClass().contains("check-box")) {
            sb.append(",  inherited -fx-text-background-color=")
                    .append(valueOf(node, "-fx-text-background-color"));
        }
        System.out.println(sb);
    }

    private static String colour(javafx.scene.paint.Paint paint) {
        if (paint instanceof javafx.scene.paint.Color c) {
            return String.format("#%02X%02X%02X (alpha %.2f)",
                    (int) Math.round(c.getRed() * 255),
                    (int) Math.round(c.getGreen() * 255),
                    (int) Math.round(c.getBlue() * 255), c.getOpacity());
        }
        return String.valueOf(paint);
    }

    private static String valueOf(Node node, String property) {
        Object value = node.getProperties().get(property);
        return value == null ? "(unset)" : colour((javafx.scene.paint.Paint) value);
    }
}
