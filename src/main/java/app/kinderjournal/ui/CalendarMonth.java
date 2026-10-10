package app.kinderjournal.ui;

import app.kinderjournal.i18n.Messages;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A month grid shared by the attendance calendar and the lesson-plan calendar.
 *
 * <p>Each day cell can show a few short chips (for example "8 present", "2 absent"
 * or a plan title), and the caller decides what a day looks like through
 * {@code decorator}. Weeks start on Monday, matching how kindergarten weeks are
 * normally planned and reported.
 */
public final class CalendarMonth {

    /** What the decorator may add to a day cell. */
    public interface Decorator {
        /**
         * @param day the date being rendered
         * @param cell the cell body; add chips or labels to it
         */
        void decorate(LocalDate day, VBox cell);
    }

    /** The badge shown in one day cell. */
    public record Marker(String text, String styleClass) {

        public static Marker of(String text, String styleClass) {
            return new Marker(text, styleClass);
        }
    }

    private YearMonth month;
    private LocalDate selected;
    private Consumer<LocalDate> onSelect;
    private Decorator decorator;
    private boolean showOtherMonthDays = true;

    public CalendarMonth(YearMonth month, LocalDate selected) {
        this.month = month;
        this.selected = selected;
    }

    public CalendarMonth onSelect(Consumer<LocalDate> handler) {
        this.onSelect = handler;
        return this;
    }

    public CalendarMonth decorator(Decorator decorator) {
        this.decorator = decorator;
        return this;
    }

    public CalendarMonth showOtherMonthDays(boolean show) {
        this.showOtherMonthDays = show;
        return this;
    }

    public YearMonth month() {
        return month;
    }

    public void setMonth(YearMonth month) {
        this.month = month;
    }

    public void setSelected(LocalDate date) {
        this.selected = date;
    }

    /** Builds the grid for the current month and selection. */
    public Node build() {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);

        // Weekday names come from the selected language, so the column headings
        // match the calendar's own month names.
        LocalDate firstColumn = Dates.startOfWeek(month.atDay(1));
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);

            Label header = new Label(Dates.weekdayShort(firstColumn.plusDays(i)));
            header.getStyleClass().add("calendar-header");
            header.setMaxWidth(Double.MAX_VALUE);
            grid.add(header, i, 0);
        }
        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(24);
        grid.getRowConstraints().add(headerRow);

        List<LocalDate> days = daysOfGrid(month);
        int row = 1;
        int column = 0;
        for (LocalDate day : days) {
            grid.add(dayCell(day), column, row);
            column++;
            if (column == 7) {
                column = 0;
                row++;
            }
        }
        return grid;
    }

    /** The 35 or 42 days shown for a month, starting on the Monday on/before the 1st. */
    public static List<LocalDate> daysOfGrid(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate start = Dates.startOfWeek(first);
        LocalDate last = month.atEndOfMonth();
        LocalDate end = Dates.startOfWeek(last).plusDays(6);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    private Node dayCell(LocalDate day) {
        boolean inMonth = YearMonth.from(day).equals(month);
        boolean isToday = day.equals(LocalDate.now());
        boolean isSelected = selected != null && selected.equals(day);

        VBox cell = new VBox(2);
        cell.getStyleClass().add("calendar-day");
        if (!inMonth) {
            cell.getStyleClass().add("calendar-day-other");
        }
        if (isToday) {
            cell.getStyleClass().add("calendar-day-today");
        }
        if (isSelected) {
            cell.getStyleClass().add("calendar-day-selected");
        }
        cell.setPadding(new Insets(4));

        HBox top = new HBox(4);
        top.setAlignment(Pos.CENTER_LEFT);
        Label number = new Label(String.valueOf(day.getDayOfMonth()));
        number.getStyleClass().add(isToday ? "calendar-day-number-today" : "calendar-day-number");
        top.getChildren().add(number);
        if (day.getDayOfMonth() == 1) {
            Label monthName = new Label(Dates.monthOnly(day));
            monthName.getStyleClass().add("calendar-day-number");
            top.getChildren().add(monthName);
        }
        top.getChildren().add(Ui.spacer());
        cell.getChildren().add(top);

        if (decorator != null && (inMonth || showOtherMonthDays)) {
            decorator.decorate(day, cell);
        }

        if (!inMonth && !showOtherMonthDays) {
            cell.setOpacity(0.35);
        }
        cell.setOnMouseClicked(event -> {
            selected = day;
            if (onSelect != null) {
                onSelect.accept(day);
            }
        });
        cell.setCursor(javafx.scene.Cursor.HAND);
        String dayText = Dates.displayLong(Dates.toIso(day));
        javafx.scene.control.Tooltip.install(cell,
                new javafx.scene.control.Tooltip(Messages.get("calendar.clickDay", dayText)));
        return cell;
    }

    // ------------------------------------------------------------------ markers

    /** Adds a marker chip to a cell, truncating text that would not fit. */
    public static void addMarker(VBox cell, String text, String styleClass, String colour) {
        Label marker = new Label(Text.summarise(text, 18));
        marker.getStyleClass().add("calendar-marker");
        marker.setMaxWidth(Double.MAX_VALUE);
        if (colour != null) {
            marker.setStyle("-fx-background-color: " + Ui.tint(colour, 0.85)
                    + "; -fx-text-fill: " + Ui.shade(colour, 0.3) + ";");
        } else if (styleClass != null) {
            marker.getStyleClass().add(styleClass);
        }
        cell.getChildren().add(marker);
    }

    /** Adds a compact "3/5" style attendance line. */
    public static void addAttendanceLine(VBox cell, int present, int absent, int recorded) {
        if (recorded == 0) {
            return;
        }
        HBox line = new HBox(4);
        line.setAlignment(Pos.CENTER_LEFT);
        Label presentLabel = new Label(Messages.get("calendar.present", present));
        presentLabel.getStyleClass().add("calendar-marker");
        presentLabel.setStyle("-fx-background-color: #e6f4ea; -fx-text-fill: #1b5e20;");
        line.getChildren().add(presentLabel);
        if (absent > 0) {
            Label absentLabel = new Label(Messages.get("calendar.absent", absent));
            absentLabel.getStyleClass().add("calendar-marker");
            absentLabel.setStyle("-fx-background-color: #fdecec; -fx-text-fill: #b3261e;");
            line.getChildren().add(absentLabel);
        }
        cell.getChildren().add(line);
    }

    /** A dot row used when there is only space for an indicator. */
    public static void addDot(VBox cell, String colour) {
        Region dot = new Region();
        dot.setMinSize(7, 7);
        dot.setPrefSize(7, 7);
        dot.setMaxSize(7, 7);
        dot.setStyle("-fx-background-color: " + colour + "; -fx-background-radius: 50%;");
        cell.getChildren().add(dot);
    }
}
