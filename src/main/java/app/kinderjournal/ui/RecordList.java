package app.kinderjournal.ui;

import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.SearchHit;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextFlow;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders the record lists that appear on several screens: timeline entries,
 * search results and the coloured date markers used in calendars.
 */
public final class RecordList {

    private RecordList() {
    }

    /**
     * One timeline entry, with a coloured marker, date, kind badge and summary.
     *
     * @param onOpen    called when the row is activated
     * @param onEdit    called when the edit affordance is used; may be null
     * @param onDelete  called when the delete affordance is used; may be null
     */
    public static Node timelineEntry(HistoryEntry entry, Consumer<HistoryEntry> onOpen,
                                     Consumer<HistoryEntry> onEdit, Consumer<HistoryEntry> onDelete,
                                     boolean showKind, boolean showDate) {
        Circle dot = new Circle(5, javafx.scene.paint.Color.web(entry.badgeColour()));
        dot.getStyleClass().add("timeline-dot");

        VBox left = new VBox(2);
        left.setAlignment(Pos.TOP_CENTER);
        left.setMinWidth(14);
        left.getChildren().add(dot);

        VBox content = new VBox(3);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox headline = new HBox(8);
        headline.setAlignment(Pos.CENTER_LEFT);
        if (showDate && !Text.isBlank(entry.date())) {
            Label date = new Label(Dates.display(entry.date()));
            date.getStyleClass().add("row-title");
            headline.getChildren().add(date);
        }
        if (showKind) {
            Label kind = Ui.chipColoured(entry.kind().label(), entry.badgeColour());
            headline.getChildren().add(kind);
        }
        if (entry.photoCount() > 0) {
            Label photos = Ui.pill("\u25a3 " + entry.photoCount(), "pill-count");
            headline.getChildren().add(photos);
        }
        Region spacer = Ui.spacer();
        headline.getChildren().add(spacer);

        if (onEdit != null) {
            headline.getChildren().add(Ui.iconButton("\u270e", "Edit this record",
                    () -> onEdit.accept(entry)));
        }
        if (onDelete != null) {
            headline.getChildren().add(Ui.iconButton("\u2715", "Delete this record",
                    () -> onDelete.accept(entry)));
        }

        content.getChildren().add(headline);

        if (!Text.isBlank(entry.title())) {
            Label title = new Label(entry.title());
            title.getStyleClass().add("row-subtitle");
            title.setWrapText(true);
            content.getChildren().add(title);
        }
        if (!Text.isBlank(entry.summary())) {
            Label summary = new Label(entry.summary());
            summary.getStyleClass().add("row-body");
            summary.setWrapText(true);
            content.getChildren().add(summary);
        }

        HBox row = new HBox(10, left, content);
        row.getStyleClass().add("list-row");
        row.setAlignment(Pos.TOP_LEFT);
        if (onOpen != null) {
            row.setOnMouseClicked(event -> {
                if (!isOnButton(event.getTarget())) {
                    onOpen.accept(entry);
                }
            });
            row.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(row, new Tooltip("Open the full record"));
        }
        return row;
    }

    /** A search result row: meta line, title and a snippet with the match in it. */
    public static Node searchResult(SearchHit hit, String keyword, Consumer<SearchHit> onOpen) {
        VBox body = new VBox(3);
        HBox.setHgrow(body, Priority.ALWAYS);

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label kind = Ui.chipColoured(hit.kind().label(), hit.kind().colour());
        meta.getChildren().add(kind);
        if (!Text.isBlank(hit.date())) {
            Label date = new Label(Dates.display(hit.date()));
            date.getStyleClass().add("row-title");
            meta.getChildren().add(date);
        }
        if (!Text.isBlank(hit.childName())) {
            Label child = new Label(hit.childName());
            child.getStyleClass().add("row-subtitle");
            meta.getChildren().add(child);
        }
        if (!Text.isBlank(hit.groupName())) {
            Label group = new Label("\u00b7 " + hit.groupName());
            group.getStyleClass().add("row-subtitle");
            meta.getChildren().add(group);
        }
        body.getChildren().add(meta);

        if (!Text.isBlank(hit.title())) {
            Label title = new Label(hit.title());
            title.getStyleClass().add("row-title");
            title.setWrapText(true);
            body.getChildren().add(title);
        }
        Node snippet = highlight(hit.snippet(), keyword);
        body.getChildren().add(snippet);

        HBox row = new HBox(10, body);
        row.getStyleClass().add("list-row");
        row.setAlignment(Pos.TOP_LEFT);
        if (onOpen != null) {
            row.setOnMouseClicked(event -> onOpen.accept(hit));
            row.setCursor(javafx.scene.Cursor.HAND);
        }
        return row;
    }

    /** A text flow with every occurrence of the keyword shown in bold accent. */
    public static TextFlow highlight(String text, String keyword) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("note-preview");
        String value = text == null ? "" : text;
        if (Text.isBlank(keyword)) {
            flow.getChildren().add(new javafx.scene.text.Text(value));
            return flow;
        }
        String lowerValue = Text.lower(value);
        String lowerKey = Text.lower(keyword);
        int from = 0;
        while (true) {
            int at = lowerValue.indexOf(lowerKey, from);
            if (at < 0) {
                flow.getChildren().add(new javafx.scene.text.Text(value.substring(from)));
                break;
            }
            if (at > from) {
                flow.getChildren().add(new javafx.scene.text.Text(value.substring(from, at)));
            }
            javafx.scene.text.Text hit = new javafx.scene.text.Text(value.substring(at, at + keyword.length()));
            hit.setStyle("-fx-font-weight: bold; -fx-fill: #0b5f59;");
            flow.getChildren().add(hit);
            from = at + keyword.length();
        }
        return flow;
    }

    /** A compact row for a class or a child in a picker list. */
    public static Node selectableRow(Node leading, String title, String subtitle, String trailing,
                                     boolean selected, Runnable onClick) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        if (selected) {
            row.getStyleClass().add("list-row-selected");
        }
        if (leading != null) {
            row.getChildren().add(leading);
        }
        VBox text = new VBox(2);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("row-title");
        text.getChildren().add(titleLabel);
        if (!Text.isBlank(subtitle)) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("row-subtitle");
            sub.setWrapText(true);
            text.getChildren().add(sub);
        }
        row.getChildren().add(text);
        if (!Text.isBlank(trailing)) {
            Label tail = new Label(trailing);
            tail.getStyleClass().add("row-subtitle");
            row.getChildren().add(tail);
        }
        if (onClick != null) {
            row.setOnMouseClicked(event -> onClick.run());
            row.setCursor(javafx.scene.Cursor.HAND);
        }
        return row;
    }

    /** A legend explaining the attendance colours, for registers and calendars. */
    public static Node attendanceLegend() {
        HBox box = new HBox(14,
                Ui.legendDot("#1b5e20", "Present"),
                Ui.legendDot("#b71c1c", "Absent"),
                Ui.legendDot("#e65100", "Late"),
                Ui.legendDot("#4527a0", "Excused"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Wraps a list of nodes in a vertical stack with consistent spacing. */
    public static VBox stack(List<? extends Node> nodes) {
        VBox box = new VBox(8);
        box.getChildren().addAll(nodes);
        return box;
    }

    /** The label used for a day heading in a grouped list, e.g. "Today \u00b7 12 Sep 2026". */
    public static String dayHeading(String iso) {
        if (Text.isBlank(iso)) {
            return "";
        }
        LocalDate date = Dates.fromIso(iso);
        if (date == null) {
            return iso;
        }
        if (date.equals(LocalDate.now())) {
            return "Today \u00b7 " + Dates.display(iso);
        }
        if (date.equals(LocalDate.now().minusDays(1))) {
            return "Yesterday \u00b7 " + Dates.display(iso);
        }
        return Dates.displayLong(iso);
    }

    private static boolean isOnButton(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (current instanceof javafx.scene.control.Button) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
