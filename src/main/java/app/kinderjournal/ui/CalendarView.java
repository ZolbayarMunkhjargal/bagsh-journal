package app.kinderjournal.ui;

import app.kinderjournal.util.Dates;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

/**
 * A month calendar with the usual previous / today / next controls.
 *
 * <p>Used by the attendance calendar and the lesson-plan calendar so both behave
 * the same way: the teacher can step month by month, jump back to today, and see
 * at a glance which days already have something recorded.
 */
public final class CalendarView {

    private final VBox root = new VBox(10);
    private final Label monthLabel = new Label();
    private final CalendarMonth grid;
    private Consumer<LocalDate> onSelect;
    private Runnable onMonthChanged;
    private YearMonth month;
    private LocalDate selected;

    public CalendarView(LocalDate initialDate, CalendarMonth.Decorator decorator) {
        this.month = YearMonth.from(initialDate);
        this.selected = initialDate;
        this.grid = new CalendarMonth(month, selected).decorator(decorator);

        Button previous = Ui.button("\u2039", () -> stepMonth(-1));
        Button next = Ui.button("\u203a", () -> stepMonth(1));
        Button today = Ui.button("Today", () -> {
            selected = LocalDate.now();
            month = YearMonth.from(selected);
            rebuild();
        });

        HBox controls = new HBox(8, previous, today, next, Ui.spacer(), monthLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        monthLabel.getStyleClass().add("section-title");

        grid.onSelect(day -> {
            selected = day;
            month = YearMonth.from(day);
            rebuild();
            if (onSelect != null) {
                onSelect.accept(day);
            }
        });

        root.getChildren().addAll(controls, grid.build());
        updateMonthLabel();
    }

    public Node node() {
        return root;
    }

    public LocalDate selected() {
        return selected;
    }

    public YearMonth month() {
        return month;
    }

    public CalendarView onSelect(Consumer<LocalDate> handler) {
        this.onSelect = handler;
        return this;
    }

    /** Called after the month changes, so the caller can re-read its data. */
    public CalendarView onMonthChanged(Runnable handler) {
        this.onMonthChanged = handler;
        return this;
    }

    /** Moves the calendar to a date without triggering the selection callback. */
    public void showDate(LocalDate date) {
        this.selected = date;
        this.month = YearMonth.from(date);
        rebuild();
    }

    /** Rebuilds the grid, e.g. after records changed. */
    public void refresh() {
        rebuild();
    }

    private void stepMonth(int delta) {
        month = month.plusMonths(delta);
        rebuild();
        if (onMonthChanged != null) {
            onMonthChanged.run();
        }
    }

    private void rebuild() {
        grid.setMonth(month);
        grid.setSelected(selected);
        root.getChildren().set(1, grid.build());
        updateMonthLabel();
    }

    private void updateMonthLabel() {
        monthLabel.setText(month.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + month.getYear()
                + (selected != null ? "   \u00b7   selected: " + Dates.display(Dates.toIso(selected)) : ""));
    }

    /** A legend row for the calendar, reused by both calendars. */
    public static Node legend(Node... items) {
        HBox box = new HBox(14, items);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
