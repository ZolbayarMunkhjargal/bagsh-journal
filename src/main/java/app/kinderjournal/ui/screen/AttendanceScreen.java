package app.kinderjournal.ui.screen;

import app.kinderjournal.db.AttendanceDao;
import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.Attendance;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.CalendarMonth;
import app.kinderjournal.ui.CalendarView;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.RecordList;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.Dates;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance: a daily register, a month calendar of what has been recorded, and
 * summaries for a chosen period.
 *
 * <p>Marks are written to the database when saved, and marking a child again on
 * the same day replaces the earlier mark rather than adding a duplicate, which is
 * what makes correcting a mistake a one-click affair.
 */
public final class AttendanceScreen implements Refreshable {

    private enum View {
        DAY, CALENDAR, SUMMARY
    }

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());
    private final DatePicker datePicker = Ui.datePicker(LocalDate.now());
    private final Label statusLine = new Label();
    private final List<Row> rows = new ArrayList<>();

    private View view = View.DAY;
    private LocalDate date;
    private LocalDate summaryFrom;
    private LocalDate summaryTo;
    private boolean dirty;
    private boolean refreshing;

    public AttendanceScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.date = context.selectedDate();
        this.summaryFrom = date.withDayOfMonth(1);
        this.summaryTo = date.withDayOfMonth(date.lengthOfMonth());
        datePicker.setValue(date);
        datePicker.valueProperty().addListener((observable, previous, next) -> {
            if (refreshing || next == null || next.equals(this.date)) {
                return;
            }
            saveIfDirty();
            this.date = next;
            context.setSelectedDate(next);
            if (view == View.DAY) {
                refresh();
            }
        });
        refresh();
    }

    @Override
    public String title() {
        return Messages.get("attendance.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("attendance.subtitle");
    }

    @Override
    public Node node() {
        return root;
    }

    /** Jumps to a specific day; used by the dashboard shortcut. */
    public void goToDate(LocalDate target) {
        if (target == null) {
            return;
        }
        saveIfDirty();
        this.date = target;
        this.view = View.DAY;
        refreshing = true;
        datePicker.setValue(target);
        refreshing = false;
        context.setSelectedDate(target);
        refresh();
    }

    // ------------------------------------------------------------------- build

    @Override
    public void refresh() {
        if (view == View.DAY) {
            saveIfDirty();
        }
        VBox page = Ui.page();
        page.getChildren().add(buildToolbar());

        switch (view) {
            case DAY -> page.getChildren().add(buildDayView());
            case CALENDAR -> page.getChildren().add(buildCalendarView());
            case SUMMARY -> page.getChildren().add(buildSummaryView());
        }
        root.setContent(page);
    }

    private Node buildToolbar() {
        VBox box = new VBox(8);

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Group> groupSelector = MainShell.groupSelector(context);

        ToggleGroup modeGroup = new ToggleGroup();
        HBox modes = new HBox();
        modes.getStyleClass().add("segmented");
        modes.getChildren().addAll(
                modeButton(Messages.get("attendance.view.day"), View.DAY, modeGroup),
                modeButton(Messages.get("attendance.view.calendar"), View.CALENDAR, modeGroup),
                modeButton(Messages.get("attendance.view.summary"), View.SUMMARY, modeGroup));

        line.getChildren().addAll(Ui.fieldLabel(Messages.get("classPicker.label")), groupSelector,
                modes, Ui.spacer());

        if (view == View.DAY) {
            line.getChildren().addAll(
                    Ui.button("\u2039", () -> goToDate(date.minusDays(1))),
                    datePicker,
                    Ui.button("\u203a", () -> goToDate(date.plusDays(1))),
                    Ui.button(Messages.get("common.today"), () -> goToDate(LocalDate.now())));
        }
        box.getChildren().add(line);

        if (view == View.DAY) {
            statusLine.getStyleClass().add("meta");
            HBox actions = new HBox(10);
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.getChildren().addAll(
                    Ui.primaryButton(Messages.get("button.saveRegister"), this::save),
                    Ui.button(Messages.get("button.markAllPresent"), this::markAllPresent),
                    Ui.button(Messages.get("button.printSheet"), this::printSheet),
                    Ui.spacer(),
                    statusLine);
            box.getChildren().add(actions);
        }

        if (context.currentGroup() == null) {
            box.getChildren().add(Ui.meta(Messages.get("attendance.noClass.body")));
        }
        return box;
    }

    private ToggleButton modeButton(String text, View target, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(view == target);
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                button.setSelected(true);
                return;
            }
            if (view == View.DAY) {
                saveIfDirty();
            }
            view = target;
            refresh();
        });
        return button;
    }

    // --------------------------------------------------------------- day view

    private Node buildDayView() {
        Group group = context.currentGroup();
        VBox box = new VBox(12);
        if (group == null) {
            box.getChildren().add(Ui.emptyState(Messages.get("attendance.noClass.title"),
                    Messages.get("attendance.noClass.body"), null));
            return box;
        }

        if (!date.equals(LocalDate.now())) {
            HBox banner = new HBox(10);
            banner.getStyleClass().add("info-banner");
            banner.setAlignment(Pos.CENTER_LEFT);
            Label label = new Label(Messages.get("attendance.editingOtherDay",
                    Dates.displayLong(Dates.toIso(date))));
            HBox.setHgrow(label, Priority.ALWAYS);
            banner.getChildren().addAll(label, Ui.button(Messages.get("button.backToToday"),
                    () -> goToDate(LocalDate.now())));
            box.getChildren().add(banner);
        }

        List<Repository.RegisterRow> register = context.repo().register(group.getId(), date);
        if (register.isEmpty()) {
            box.getChildren().add(Ui.emptyState(
                    Messages.get("attendance.noChildren.title", group.getName()),
                    Messages.get("attendance.noChildren.body"),
                    Ui.primaryButton(Messages.get("attendance.noChildren.action"),
                            () -> shell.show("children"))));
            return box;
        }

        rows.clear();

        VBox registerCard = new VBox(0);
        registerCard.getStyleClass().add("card");
        registerCard.getChildren().add(registerHeader());

        int index = 1;
        for (Repository.RegisterRow registerRow : register) {
            Row row = new Row(registerRow);
            rows.add(row);
            registerCard.getChildren().add(row.rowNode(index++));
        }

        box.getChildren().add(registerCard);
        box.getChildren().add(buildDaySummary(register));
        box.getChildren().add(RecordList.attendanceLegend());
        updateStatus();
        return box;
    }

    private Node registerHeader() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(6, 12, 6, 12));
        applyColumnConstraints(grid);
        String[] titles = {
                Messages.get("attendance.column.number"),
                Messages.get("attendance.column.child"),
                Messages.get("attendance.column.mark"),
                Messages.get("attendance.column.reason"),
                Messages.get("attendance.column.times")};
        for (int i = 0; i < titles.length; i++) {
            Label label = new Label(titles[i]);
            label.getStyleClass().add("field-label");
            grid.add(label, i, 0);
        }
        grid.setStyle("-fx-border-color: transparent transparent #dfe4e8 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        return grid;
    }

    private void applyColumnConstraints(GridPane grid) {
        grid.setHgap(10);
        grid.setVgap(6);
        // Percentage columns are measured against the grid's width, and the gaps
        // are taken out of the same space, so these must add up to well under 100:
        // five 10px gaps plus the card's own padding have to fit too. At 100 the
        // last column was pushed past the right-hand edge of the window.
        double[] widths = {4, 22, 28, 26, 12};
        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            grid.getColumnConstraints().add(column);
        }
    }

    private Node buildDaySummary(List<Repository.RegisterRow> register) {
        int present = 0;
        int absent = 0;
        int late = 0;
        int excused = 0;
        for (Repository.RegisterRow row : register) {
            switch (row.status()) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case LATE -> late++;
                case EXCUSED -> excused++;
            }
        }
        int total = register.size();
        int rate = total == 0 ? -1 : (int) Math.round((present + late) * 100.0 / total);

        // A FlowPane, not an HBox: six tiles at 112px plus five gaps come to 722px,
        // and an HBox insists on that width, which made the whole register wider
        // than the window and pushed its right-hand column out of view. Wrapping
        // the tiles keeps the row inside whatever space the card has.
        javafx.scene.layout.FlowPane tiles = new javafx.scene.layout.FlowPane(10, 10,
                summaryChip(String.valueOf(present), Messages.get("attendance.present"), "#1b5e20"),
                summaryChip(String.valueOf(absent), Messages.get("attendance.absent"), "#b71c1c"),
                summaryChip(String.valueOf(late), Messages.get("attendance.late"), "#e65100"),
                summaryChip(String.valueOf(excused), Messages.get("attendance.excused"), "#4527a0"),
                summaryChip(rate < 0 ? "\u2013" : rate + "%",
                        Messages.get("attendance.attendingToday"), "#0f766e"),
                summaryChip(String.valueOf(total), Messages.get("attendance.onRegister"),
                        "#37474f"));
        return tiles;
    }

    private Node summaryChip(String value, String label, String colour) {
        VBox box = new VBox(1);
        box.getStyleClass().add("stat-tile");
        box.setPrefWidth(128);
        box.setMinWidth(104);
        box.setMaxWidth(128);
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                + Ui.shade(colour, 0.1) + ";");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");
        labelLabel.setWrapText(true);
        box.getChildren().addAll(valueLabel, labelLabel);
        return box;
    }

    // -------------------------------------------------------------- one row

    /** A single child's line in the register. */
    private final class Row {

        private final Repository.RegisterRow data;
        private final ToggleGroup group = new ToggleGroup();
        private final TextField reason =
                Ui.textField("", Messages.get("attendance.reasonHint"), 22);
        private final TextField arrival = Ui.textField("", Messages.get("attendance.arrivalHint"), 6);
        private final TextField departure =
                Ui.textField("", Messages.get("attendance.departureHint"), 6);
        private final Map<Choices.AttendanceStatus, ToggleButton> buttons = new LinkedHashMap<>();

        Row(Repository.RegisterRow data) {
            this.data = data;
            Attendance attendance = data.attendance();
            reason.setText(attendance.getReason());
            arrival.setText(attendance.getArrivalTime());
            departure.setText(attendance.getDepartureTime());

            reason.textProperty().addListener((observable, previous, next) -> {
                attendance.setReason(next == null ? "" : next);
                markDirty();
            });
            arrival.textProperty().addListener((observable, previous, next) -> {
                attendance.setArrivalTime(next == null ? "" : next);
                markDirty();
            });
            departure.textProperty().addListener((observable, previous, next) -> {
                attendance.setDepartureTime(next == null ? "" : next);
                markDirty();
            });
        }

        Node rowNode(int index) {
            GridPane grid = new GridPane();
            grid.setPadding(new Insets(4, 12, 4, 12));
            applyColumnConstraints(grid);

            Label number = new Label(String.valueOf(index));
            number.getStyleClass().add("meta");
            grid.add(number, 0, 0);

            HBox child = new HBox(8);
            child.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(data.child().fullName());
            name.getStyleClass().add("body-text");
            child.getChildren().addAll(Ui.avatar(data.child(), 13), name);
            grid.add(child, 1, 0);

            HBox marks = new HBox(4);
            marks.getStyleClass().add("segmented");
            for (Choices.AttendanceStatus status : Choices.AttendanceStatus.values()) {
                ToggleButton button = new ToggleButton(status.shortCode());
                button.setToggleGroup(group);
                button.setTooltip(new Tooltip(status.label()));
                button.setSelected(data.status() == status);
                button.setOnAction(event -> {
                    if (!button.isSelected()) {
                        button.setSelected(true);
                        return;
                    }
                    data.setStatus(status);
                    markDirty();
                    applyRowStyle(grid, status);
                });
                buttons.put(status, button);
                marks.getChildren().add(button);
            }
            grid.add(marks, 2, 0);
            grid.add(reason, 3, 0);

            HBox times = new HBox(4, arrival, new Label("\u2013"), departure);
            times.setAlignment(Pos.CENTER_LEFT);
            grid.add(times, 4, 0);

            applyRowStyle(grid, data.status());
            return grid;
        }

        private void applyRowStyle(GridPane grid, Choices.AttendanceStatus status) {
            String background = switch (status) {
                case PRESENT -> "transparent";
                case ABSENT -> "#fffafa";
                case LATE -> "#fffdf7";
                case EXCUSED -> "#fbfaff";
            };
            grid.setStyle("-fx-border-color: transparent transparent #eef1f4 transparent;"
                    + " -fx-background-color: " + background + ";");
        }
    }

    // ----------------------------------------------------------------- saving

    private void markDirty() {
        dirty = true;
        updateStatus();
    }

    private void updateStatus() {
        if (statusLine == null) {
            return;
        }
        statusLine.setText(dirty
                ? Messages.get("attendance.unsavedChanges")
                : Messages.get("attendance.allChangesSaved"));
        statusLine.setStyle(dirty
                ? "-fx-text-fill: #b45309; -fx-font-weight: bold;"
                : "-fx-text-fill: #64748b;");
    }

    private void saveIfDirty() {
        if (dirty && view == View.DAY) {
            save();
        }
    }

    private void save() {
        Group group = context.currentGroup();
        if (group == null || rows.isEmpty()) {
            dirty = false;
            return;
        }
        List<Repository.RegisterRow> data = new ArrayList<>();
        for (Row row : rows) {
            data.add(row.data);
        }
        try {
            context.repo().saveRegister(group.getId(), date, data);
            dirty = false;
            updateStatus();
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error(Messages.get("attendance.error.save"), ex);
        }
    }

    private void markAllPresent() {
        for (Row row : rows) {
            row.data.setStatus(Choices.AttendanceStatus.PRESENT);
            ToggleButton button = row.buttons.get(Choices.AttendanceStatus.PRESENT);
            if (button != null) {
                button.setSelected(true);
            }
        }
        markDirty();
        refresh();
    }

    // ------------------------------------------------------------ calendar view

    private Node buildCalendarView() {
        Group group = context.currentGroup();
        if (group == null) {
            return Ui.emptyState(Messages.get("attendance.noClass.title"),
                    Messages.get("attendance.noClass.calendarBody"), null);
        }
        YearMonth month = YearMonth.from(date);
        Map<String, AttendanceDao.Summary> byDate = context.repo().attendance()
                .summariesByDate(group.getId(), month.atDay(1), month.atEndOfMonth());

        CalendarView calendar = new CalendarView(date, (day, cell) -> {
            AttendanceDao.Summary summary = byDate.get(Dates.toIso(day));
            if (summary != null && summary.recorded() > 0) {
                CalendarMonth.addAttendanceLine(cell, summary.attending(),
                        summary.absent(), summary.recorded());
            }
            int observations = context.repo().observations().countOn(day);
            if (observations > 0) {
                CalendarMonth.addMarker(cell, Messages.get("attendance.observations", observations),
                        null, "#1565c0");
            }
        });
        calendar.onSelect(day -> {
            this.date = day;
            this.view = View.DAY;
            refreshing = true;
            datePicker.setValue(day);
            refreshing = false;
            context.setSelectedDate(day);
            refresh();
        });

        String monthName = Dates.monthName(month.getYear(), month.getMonthValue());
        VBox box = new VBox(12);
        box.getChildren().add(Ui.cardWithHeader(
                Messages.get("attendance.calendarTitle", monthName),
                Ui.meta(Messages.get("attendance.calendarHint")), calendar.node()));
        box.getChildren().add(RecordList.attendanceLegend());
        return box;
    }

    // ------------------------------------------------------------- summary view

    private Node buildSummaryView() {
        Group group = context.currentGroup();
        if (group == null) {
            return Ui.emptyState(Messages.get("attendance.noClass.title"),
                    Messages.get("attendance.noClass.summaryBody"), null);
        }

        DatePicker fromPicker = Ui.datePicker(summaryFrom);
        DatePicker toPicker = Ui.datePicker(summaryTo);
        HBox controls = new HBox(10,
                Ui.fieldLabel(Messages.get("common.from")), fromPicker,
                Ui.fieldLabel(Messages.get("common.to")), toPicker,
                Ui.button(Messages.get("common.update"), () -> {
                    summaryFrom = fromPicker.getValue();
                    summaryTo = toPicker.getValue();
                    refresh();
                }),
                Ui.spacer(),
                Ui.button(Messages.get("button.printSummary"), this::printSummary));
        controls.setAlignment(Pos.CENTER_LEFT);

        List<Child> children = context.repo().children().listByGroup(group.getId(), false);
        Map<Long, AttendanceDao.Summary> perChild = context.repo().attendance()
                .summariesByChild(children, summaryFrom, summaryTo);
        AttendanceDao.Summary total = context.repo().attendance()
                .summaryForGroup(group.getId(), summaryFrom, summaryTo);

        VBox box = new VBox(12);
        box.getChildren().add(Ui.card(Messages.get("attendance.periodCard"),
                controls,
                Ui.meta(Messages.get("attendance.periodLine",
                        Dates.display(Dates.toIso(summaryFrom)),
                        Dates.display(Dates.toIso(summaryTo)))
                        + "  \u00b7  " + Messages.get("attendance.periodMarks", total.recorded())
                        + "  \u00b7  " + Messages.get("attendance.periodRate", total.rateText()))));

        if (children.isEmpty()) {
            box.getChildren().add(Ui.emptyState(Messages.get("attendance.summary.empty"),
                    Messages.get("attendance.summary.emptyBody"), null));
            return box;
        }

        GridPane table = new GridPane();
        table.setHgap(10);
        table.setVgap(4);
        String[] titles = {
                Messages.get("attendance.column.child"),
                Messages.get("attendance.present"),
                Messages.get("attendance.late"),
                Messages.get("attendance.absent"),
                Messages.get("attendance.excused"),
                Messages.get("attendance.column.recorded"),
                Messages.get("attendance.column.rate")};
        // 10% is left over for the six gaps between the columns: the columns plus
        // the gaps must not exceed the width the scroll pane can give them.
        double[] widths = {24, 12, 12, 12, 12, 12, 12};
        for (int i = 0; i < titles.length; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(widths[i]);
            table.getColumnConstraints().add(column);
            Label label = new Label(titles[i]);
            label.getStyleClass().add("field-label");
            table.add(label, i, 0);
        }

        int row = 1;
        for (Child child : children) {
            AttendanceDao.Summary summary = perChild.getOrDefault(child.getId(),
                    AttendanceDao.Summary.EMPTY);
            table.add(new Label(child.fullName()), 0, row);
            table.add(numberCell(summary.present()), 1, row);
            table.add(numberCell(summary.late()), 2, row);
            table.add(numberCell(summary.absent()), 3, row);
            table.add(numberCell(summary.excused()), 4, row);
            table.add(numberCell(summary.recorded()), 5, row);
            table.add(rateCell(summary), 6, row);
            row++;
        }
        table.add(boldCell(Messages.get("attendance.classTotal")), 0, row);
        table.add(numberCell(total.present()), 1, row);
        table.add(numberCell(total.late()), 2, row);
        table.add(numberCell(total.absent()), 3, row);
        table.add(numberCell(total.excused()), 4, row);
        table.add(numberCell(total.recorded()), 5, row);
        table.add(rateCell(total), 6, row);

        box.getChildren().add(Ui.card(Messages.get("attendance.perChild"), table));
        return box;
    }

    private Label numberCell(int value) {
        Label label = new Label(String.valueOf(value));
        label.getStyleClass().add("body-text");
        return label;
    }

    private Label boldCell(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("row-title");
        return label;
    }

    private Label rateCell(AttendanceDao.Summary summary) {
        Label label = new Label(summary.rateText());
        label.getStyleClass().add("row-title");
        if (summary.ratePercent() >= 0 && summary.ratePercent() < 80) {
            label.setStyle("-fx-text-fill: #b45309;");
        }
        return label;
    }

    // ------------------------------------------------------------------ printing

    private void printSheet() {
        Group group = context.currentGroup();
        if (group == null) {
            return;
        }
        saveIfDirty();
        long groupId = group.getId();
        LocalDate sheetDate = date;
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetAttendanceDay(groupId, sheetDate);
            }
        });
    }

    private void printSummary() {
        Group group = context.currentGroup();
        if (group == null) {
            return;
        }
        long groupId = group.getId();
        LocalDate from = summaryFrom;
        LocalDate to = summaryTo;
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetAttendanceSummary(groupId, from, to);
            }
        });
    }
}
