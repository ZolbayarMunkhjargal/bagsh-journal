package app.kinderjournal.ui.screen;

import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.LessonPlan;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.CalendarMonth;
import app.kinderjournal.ui.CalendarView;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lesson and activity planning: a day view, a week view and a month calendar.
 *
 * <p>Plans belong to a class and cover one day or a range, so a week-long project
 * appears on every day it runs. Any plan can be saved as a template or copied to
 * another date, which is how the weekly routine is kept without retyping it.
 */
public final class PlansScreen implements Refreshable {

    private enum View {
        DAY, WEEK, CALENDAR
    }

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    private View view = View.DAY;
    private LocalDate date = LocalDate.now();
    private boolean showAllGroups;

    public PlansScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.date = context.selectedDate();
        refresh();
    }

    @Override
    public String title() {
        return "Lesson plans";
    }

    @Override
    public String subtitle() {
        return "Plan activities for a day or a whole week, then record what actually happened.";
    }

    @Override
    public Node node() {
        return root;
    }

    /** Opens the plan form for a specific day. */
    public void openNewForm(LocalDate day) {
        this.date = day == null ? LocalDate.now() : day;
        openForm(null, this.date);
    }

    // ------------------------------------------------------------------- build

    @Override
    public void refresh() {
        VBox page = Ui.page();
        page.getChildren().add(buildToolbar());

        if (context.currentGroup() == null) {
            page.getChildren().add(Ui.emptyState("No class selected",
                    "Activities are planned for a class. Create one to get started.",
                    Ui.primaryButton("Go to classes", () -> shell.show("classes"))));
            root.setContent(page);
            return;
        }

        switch (view) {
            case DAY -> {
                page.getChildren().add(buildDayView());
                page.getChildren().add(buildTemplatesCard());
            }
            case WEEK -> page.getChildren().add(buildWeekView());
            case CALENDAR -> page.getChildren().add(buildCalendarView());
        }
        root.setContent(page);
    }

    private Node buildToolbar() {
        VBox box = new VBox(8);

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Group> selector = MainShell.groupSelector(context);

        ToggleGroup modeGroup = new ToggleGroup();
        HBox modes = new HBox();
        modes.getStyleClass().add("segmented");
        modes.getChildren().addAll(
                modeButton("Day", View.DAY, modeGroup),
                modeButton("Week", View.WEEK, modeGroup),
                modeButton("Month", View.CALENDAR, modeGroup));

        line.getChildren().addAll(Ui.fieldLabel("Class"), selector, modes, Ui.spacer());
        line.getChildren().addAll(
                Ui.button("\u2039", this::previousPeriod),
                Ui.button("Today", () -> {
                    date = LocalDate.now();
                    context.setSelectedDate(date);
                    refresh();
                }),
                Ui.button("\u203a", this::nextPeriod),
                Ui.primaryButton("Plan an activity", () -> openForm(null, date)));
        box.getChildren().add(line);

        HBox extras = new HBox(12);
        extras.setAlignment(Pos.CENTER_LEFT);
        CheckBox allGroups = new CheckBox("Show plans for every class");
        allGroups.setSelected(showAllGroups);
        allGroups.selectedProperty().addListener((observable, previous, next) -> {
            showAllGroups = next;
            refresh();
        });
        extras.getChildren().addAll(
                allGroups,
                Ui.spacer(),
                Ui.button("Print weekly plan", this::printWeek),
                Ui.button("Print daily plan", this::printDay));
        box.getChildren().add(extras);
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
            view = target;
            refresh();
        });
        return button;
    }

    private void previousPeriod() {
        date = switch (view) {
            case DAY -> date.minusDays(1);
            case WEEK -> date.minusWeeks(1);
            case CALENDAR -> date.minusMonths(1);
        };
        context.setSelectedDate(date);
        refresh();
    }

    private void nextPeriod() {
        date = switch (view) {
            case DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case CALENDAR -> date.plusMonths(1);
        };
        context.setSelectedDate(date);
        refresh();
    }

    private long effectiveGroupId() {
        if (showAllGroups) {
            return 0;
        }
        return context.currentGroupId();
    }

    // --------------------------------------------------------------- day view

    private Node buildDayView() {
        List<LessonPlan> plans = context.repo().plans().listForDay(effectiveGroupId(), date);
        VBox box = new VBox(12);

        HBox banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label(Dates.displayLong(Dates.toIso(date)));
        heading.getStyleClass().add("section-title");
        banner.getChildren().addAll(heading, Ui.spacer(),
                Ui.meta(plans.isEmpty() ? "nothing planned"
                        : Text.plural(plans.size(), "activity", "activities")));
        box.getChildren().add(banner);

        if (plans.isEmpty()) {
            box.getChildren().add(Ui.emptyState("Nothing planned for this day",
                    "Add an activity with its objectives and materials, and it will also appear on "
                            + "the weekly plan you can print for the classroom.",
                    Ui.primaryButton("Plan an activity", () -> openForm(null, date))));
            return box;
        }

        for (LessonPlan plan : plans) {
            box.getChildren().add(planCard(plan, true));
        }
        return box;
    }

    /** A full plan card with its details and the actions for it. */
    private Node planCard(LessonPlan plan, boolean showDetail) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(plan.getTitle());
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        head.getChildren().add(title);
        head.getChildren().addAll(
                Ui.pill(plan.statusEnum().label(), statusStyle(plan)),
                Ui.pill(plan.areaEnum().label(), "pill-count"));
        if (plan.isRange()) {
            head.getChildren().add(Ui.pill(plan.dateRangeLabel(), "pill-planned"));
        }
        if (plan.isTemplate()) {
            head.getChildren().add(Ui.pill("template", "pill-count"));
        }
        head.getChildren().add(Ui.spacer());
        head.getChildren().addAll(
                Ui.button("Edit", () -> openForm(plan, date)),
                Ui.button("Copy to\u2026", () -> copyPlan(plan)),
                Ui.button("Save as template", () -> saveAsTemplate(plan)),
                Ui.dangerButton("Delete", () -> delete(plan)));
        card.getChildren().add(head);

        Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                plan.getGroupName().isEmpty() ? "all classes" : plan.getGroupName(),
                plan.timeLabel(),
                plan.isRange() ? "" : ""));
        meta.getStyleClass().add("meta");
        card.getChildren().add(meta);

        if (showDetail) {
            if (!Text.isBlank(plan.getObjectives())) {
                card.getChildren().add(detailRow("Learning objectives", plan.getObjectives()));
            }
            if (!Text.isBlank(plan.getDescription())) {
                card.getChildren().add(detailRow("Planned activities", plan.getDescription()));
            }
            if (!Text.isBlank(plan.getSequence())) {
                card.getChildren().add(detailRow("Sequence", plan.getSequence()));
            }
            if (!Text.isBlank(plan.getMaterials())) {
                card.getChildren().add(detailRow("Materials needed", plan.getMaterials()));
            }
            if (!Text.isBlank(plan.getInstructions())) {
                card.getChildren().add(detailRow("Notes for the teacher", plan.getInstructions()));
            }
            if (!Text.isBlank(plan.getReflection())) {
                card.getChildren().add(detailRow("Reflection after the activity", plan.getReflection()));
            }
        }

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().add(Ui.fieldLabel("After the activity:"));
        for (Choices.PlanStatus status : Choices.PlanStatus.values()) {
            statusRow.getChildren().add(Ui.button(status.label(),
                    () -> setStatus(plan, status)));
        }
        card.getChildren().add(statusRow);
        return card;
    }

    private Node detailRow(String label, String value) {
        VBox box = new VBox(2);
        box.getChildren().add(Ui.fieldLabel(label));
        Label text = new Label(value);
        text.setWrapText(true);
        text.getStyleClass().add("body-text");
        box.getChildren().add(text);
        return box;
    }

    private String statusStyle(LessonPlan plan) {
        return switch (plan.statusEnum()) {
            case PLANNED -> "pill-planned";
            case COMPLETED -> "pill-completed";
            case POSTPONED -> "pill-postponed";
            case CANCELLED -> "pill-cancelled";
        };
    }

    private void setStatus(LessonPlan plan, Choices.PlanStatus status) {
        if (status == Choices.PlanStatus.COMPLETED) {
            // A reflection is optional but is the point of marking it complete.
            askReflection(plan);
            return;
        }
        context.repo().plans().setStatusAndReflection(plan.getId(), status.id(), plan.getReflection());
        context.notifyDataChanged();
    }

    private void askReflection(LessonPlan plan) {
        Stage dialog = modalStage("How did it go?");
        TextArea reflection = Ui.textArea(plan.getReflection(), 5);
        reflection.setPromptText("Optional: what worked, what to change next time, "
                + "how the children responded");
        VBox body = new VBox(10,
                Ui.meta("Marking \"" + plan.getTitle() + "\" as completed."),
                Ui.field("Reflection", reflection));
        body.setPadding(new Insets(16, 18, 16, 18));
        body.setPrefWidth(520);

        HBox buttons = new HBox(8, Ui.spacer(),
                Ui.button("Cancel", dialog::close),
                Ui.primaryButton("Mark completed", () -> {
                    context.repo().plans().setStatusAndReflection(plan.getId(),
                            Choices.PlanStatus.COMPLETED.id(), reflection.getText().trim());
                    context.notifyDataChanged();
                    dialog.close();
                }));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 18, 16, 18));
        showDialog(dialog, new VBox(body, buttons));
        Platform.runLater(reflection::requestFocus);
    }

    // -------------------------------------------------------------- week view

    private Node buildWeekView() {
        LocalDate start = Dates.startOfWeek(date);
        Map<String, List<LessonPlan>> byDay = context.repo().plans()
                .byDay(effectiveGroupId(), start, start.plusDays(6));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);

            LocalDate day = start.plusDays(i);
            boolean today = day.equals(LocalDate.now());
            VBox columnBox = new VBox(6);
            columnBox.getStyleClass().add("card");
            columnBox.setPadding(new Insets(8));
            columnBox.setMinHeight(180);

            HBox header = new HBox(6);
            header.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            name.getStyleClass().add("field-label");
            Label number = new Label(Dates.display(Dates.toIso(day)));
            number.getStyleClass().add(today ? "row-title" : "row-subtitle");
            header.getChildren().addAll(name, number);
            if (today) {
                header.getChildren().add(Ui.pill("today", "pill-completed"));
            }
            columnBox.getChildren().add(header);

            List<LessonPlan> plans = byDay.getOrDefault(Dates.toIso(day), List.of());
            if (plans.isEmpty()) {
                Label none = new Label("\u2013");
                none.getStyleClass().add("field-empty");
                columnBox.getChildren().add(none);
            } else {
                for (LessonPlan plan : plans) {
                    columnBox.getChildren().add(weekChip(plan));
                }
            }

            makeDayClickable(columnBox, day);
            grid.add(columnBox, i, 0);
        }

        VBox box = new VBox(12);
        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Week of " + Dates.display(Dates.toIso(start)));
        title.getStyleClass().add("section-title");
        heading.getChildren().addAll(title, Ui.spacer(),
                Ui.meta("Click a day to plan or edit that day"),
                Ui.button("Plan an activity", () -> openForm(null, date)));
        box.getChildren().add(heading);
        box.getChildren().add(grid);
        box.getChildren().add(legendRow());
        return box;
    }

    private void makeDayClickable(VBox columnBox, LocalDate day) {
        columnBox.setOnMouseClicked(event -> {
            date = day;
            view = View.DAY;
            context.setSelectedDate(day);
            refresh();
        });
        columnBox.setCursor(javafx.scene.Cursor.HAND);
        javafx.scene.control.Tooltip.install(columnBox,
                new javafx.scene.control.Tooltip("Open " + Dates.display(Dates.toIso(day))));
    }

    private Node weekChip(LessonPlan plan) {
        VBox chip = new VBox(1);
        chip.getStyleClass().add("list-row");
        chip.setPadding(new Insets(5, 7, 5, 7));
        Label title = new Label(Text.summarise(plan.getTitle(), 34));
        title.getStyleClass().add("row-body");
        title.setWrapText(true);
        Label meta = new Label(Text.joinNonBlank(" \u00b7 ", plan.timeLabel(),
                plan.statusEnum().label()));
        meta.getStyleClass().add("stat-hint");
        meta.setWrapText(true);
        chip.getChildren().addAll(title, meta);
        chip.setStyle("-fx-border-color: transparent transparent transparent "
                + statusColour(plan) + "; -fx-border-width: 0 0 0 3;");
        chip.setOnMouseClicked(event -> openForm(plan, plan.localDate()));
        chip.setCursor(javafx.scene.Cursor.HAND);
        return chip;
    }

    private String statusColour(LessonPlan plan) {
        return switch (plan.statusEnum()) {
            case PLANNED -> "#1d4ed8";
            case COMPLETED -> "#1b5e20";
            case POSTPONED -> "#b45309";
            case CANCELLED -> "#546e7a";
        };
    }

    private Node legendRow() {
        HBox box = new HBox(14);
        box.setAlignment(Pos.CENTER_LEFT);
        for (Choices.PlanStatus status : Choices.PlanStatus.values()) {
            String colour = switch (status) {
                case PLANNED -> "#1d4ed8";
                case COMPLETED -> "#1b5e20";
                case POSTPONED -> "#b45309";
                case CANCELLED -> "#546e7a";
            };
            box.getChildren().add(Ui.legendDot(colour, status.label()));
        }
        return box;
    }

    // ----------------------------------------------------------- month view

    private Node buildCalendarView() {
        YearMonth month = YearMonth.from(date);
        Map<String, List<LessonPlan>> byDay = context.repo().plans()
                .byDay(effectiveGroupId(), month.atDay(1), month.atEndOfMonth());

        CalendarView calendar = new CalendarView(date, (day, cell) -> {
            List<LessonPlan> plans = byDay.get(Dates.toIso(day));
            if (plans == null) {
                return;
            }
            int shown = 0;
            for (LessonPlan plan : plans) {
                if (shown++ >= 2) {
                    CalendarMonth.addMarker(cell, "+" + (plans.size() - 2) + " more", null, "#546e7a");
                    break;
                }
                CalendarMonth.addMarker(cell, plan.getTitle(), null, statusColour(plan));
            }
        });
        calendar.onSelect(day -> {
            date = day;
            view = View.DAY;
            context.setSelectedDate(day);
            refresh();
        });

        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        VBox box = new VBox(12);
        box.getChildren().add(Ui.cardWithHeader(
                "Activity plan \u00b7 " + monthName + " " + month.getYear(),
                Ui.meta("Click a day to open it"),
                calendar.node()));
        box.getChildren().add(legendRow());
        return box;
    }

    // -------------------------------------------------------------- templates

    private Node buildTemplatesCard() {
        List<LessonPlan> templates = context.repo().plans().listTemplates();
        VBox list = new VBox(6);
        if (templates.isEmpty()) {
            list.getChildren().add(Ui.meta("No templates yet. Open a plan and choose "
                    + "\"Save as template\" to reuse it later."));
        } else {
            for (LessonPlan template : templates) {
                HBox line = new HBox(10);
                line.setAlignment(Pos.CENTER_LEFT);
                Label title = new Label(template.getTitle());
                title.getStyleClass().add("body-text");
                line.getChildren().addAll(title,
                        Ui.pill(template.areaEnum().label(), "pill-count"),
                        Ui.spacer(),
                        Ui.linkButton("Use on " + Dates.display(Dates.toIso(date)),
                                () -> createFromTemplate(template)),
                        Ui.linkButton("Delete", () -> delete(template)));
                list.getChildren().add(line);
            }
        }
        return Ui.cardWithHeader("Templates", null, list);
    }

    private void createFromTemplate(LessonPlan template) {
        layoutFromTemplate(template, date);
    }

    /** Opens the plan form pre-filled from a template, ready to adjust. */
    private void layoutFromTemplate(LessonPlan template, LocalDate day) {
        LessonPlan draft = template.clone();
        draft.setId(0);
        draft.setTemplate(false);
        draft.setDate(Dates.toIso(day));
        draft.setEndDate(null);
        draft.setStatusEnum(Choices.PlanStatus.PLANNED);
        draft.setReflection("");
        openFormWith(draft, false);
    }

    private void saveAsTemplate(LessonPlan plan) {
        try {
            context.repo().plans().duplicate(plan.getId(), null, true);
            Ui.info("Template saved",
                    "\"" + plan.getTitle() + "\" can now be reused from the templates list.");
            refresh();
        } catch (RuntimeException ex) {
            Ui.error("Could not save this template", ex);
        }
    }

    private void copyPlan(LessonPlan plan) {
        DatePicker picker = Ui.datePicker(date.plusDays(7));
        Stage dialog = modalStage("Copy \"" + plan.getTitle() + "\"");
        VBox body = new VBox(10,
                Ui.meta("A copy is created as a new planned activity, keeping the title, "
                        + "objectives and materials."),
                Ui.field("New date", picker));
        body.setPadding(new Insets(16, 18, 16, 18));
        body.setPrefWidth(420);
        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton("Create copy", () -> {
                    if (picker.getValue() == null) {
                        return;
                    }
                    try {
                        long id = context.repo().plans().duplicate(plan.getId(), picker.getValue(), false);
                        context.repo().plans().setStatusAndReflection(id,
                                Choices.PlanStatus.PLANNED.id(), "");
                        context.notifyDataChanged();
                        dialog.close();
                    } catch (RuntimeException ex) {
                        Ui.error("Could not copy this plan", ex);
                    }
                }));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 18, 16, 18));
        showDialog(dialog, new VBox(body, buttons));
    }

    private void delete(LessonPlan plan) {
        if (!Ui.confirmDestructive("Delete this " + (plan.isTemplate() ? "template" : "activity") + "?",
                "\"" + plan.getTitle() + "\" will be removed. This cannot be undone.",
                "Delete")) {
            return;
        }
        context.repo().plans().delete(plan.getId());
        context.notifyDataChanged();
    }

    // -------------------------------------------------------------------- form

    private void openForm(LessonPlan existing, LocalDate defaultDate) {
        if (existing == null) {
            LessonPlan draft = new LessonPlan();
            draft.setDate(Dates.toIso(defaultDate == null ? LocalDate.now() : defaultDate));
            draft.setGroupId(context.currentGroupId());
            draft.setStatusEnum(Choices.PlanStatus.PLANNED);
            openFormWith(draft, true);
        } else {
            openFormWith(existing, false);
        }
    }

    private void openFormWith(LessonPlan draft, boolean creating) {
        Stage dialog = modalStage(creating ? "Plan an activity" : "Edit activity");

        TextField title = Ui.textField(draft.getTitle(), "e.g. Story sequencing", 30);
        DatePicker start = Ui.datePicker(Dates.fromIso(draft.getDate()));
        DatePicker end = Ui.datePicker(Dates.fromIso(draft.effectiveEndDate()));
        TextField startTime = Ui.textField(draft.getStartTime(), "09:30", 8);
        TextField endTime = Ui.textField(draft.getEndTime(), "10:15", 8);

        ComboBox<Group> group = new ComboBox<>();
        List<Group> groups = context.repo().groups().listActive();
        group.getItems().add(null);
        group.getItems().addAll(groups);
        group.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group value) {
                return value == null ? "(all classes)" : value.getName();
            }

            @Override
            public Group fromString(String text) {
                return null;
            }
        });
        for (Group candidate : groups) {
            if (candidate.getId() == draft.getGroupId()) {
                group.setValue(candidate);
            }
        }
        group.setPrefWidth(200);

        ComboBox<Choices.PlanArea> area = new ComboBox<>();
        area.getItems().addAll(Choices.PlanArea.values());
        area.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choices.PlanArea value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Choices.PlanArea fromString(String text) {
                return null;
            }
        });
        area.setValue(draft.areaEnum());
        area.setPrefWidth(220);

        ComboBox<Choices.PlanStatus> status = new ComboBox<>();
        status.getItems().addAll(Choices.PlanStatus.values());
        status.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choices.PlanStatus value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Choices.PlanStatus fromString(String text) {
                return null;
            }
        });
        status.setValue(draft.statusEnum());
        status.setPrefWidth(160);

        TextArea objectives = Ui.textArea(draft.getObjectives(), 3);
        objectives.setPromptText("What should the children learn or practise?");
        TextArea description = Ui.textArea(draft.getDescription(), 4);
        description.setPromptText("What will happen during the activity?");
        TextArea sequence = Ui.textArea(draft.getSequence(), 4);
        sequence.setPromptText("Step by step, e.g.\n1. Gather on the carpet\n2. Read the story\n"
                + "3. Order the picture cards");
        TextArea materials = Ui.textArea(draft.getMaterials(), 3);
        materials.setPromptText("Everything needed, one per line");
        TextArea instructions = Ui.textArea(draft.getInstructions(), 3);
        instructions.setPromptText("Anything to remember on the day");
        TextArea reflection = Ui.textArea(draft.getReflection(), 3);
        reflection.setPromptText("Written afterwards: how it went, what to change");

        CheckBox template = new CheckBox("Keep as a reusable template");
        template.setSelected(draft.isTemplate());
        CheckBox recurring = new CheckBox("Part of the weekly routine");
        recurring.setSelected(draft.isRecurring());

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field("Title", title), 0, 0, 2, 1);
        grid.add(Ui.field("Class", group), 0, 1);
        grid.add(Ui.field("Subject area", area), 1, 1);
        grid.add(Ui.field("Date", start), 0, 2);
        grid.add(Ui.field("Last day (for a multi-day project)", end), 1, 2);
        grid.add(Ui.field("Starts", startTime), 0, 3);
        grid.add(Ui.field("Ends", endTime), 1, 3);
        grid.add(Ui.field("Status", status), 0, 4);
        grid.add(Ui.row(template, recurring), 1, 4);
        grid.add(Ui.field("Learning objectives", objectives), 0, 5, 2, 1);
        grid.add(Ui.field("Planned activities", description), 0, 6, 2, 1);
        grid.add(Ui.field("Sequence of activities", sequence), 0, 7, 2, 1);
        grid.add(Ui.field("Materials", materials), 0, 8, 2, 1);
        grid.add(Ui.field("Notes for the teacher", instructions), 0, 9, 2, 1);
        grid.add(Ui.field("Reflection after the activity", reflection), 0, 10, 2, 1);

        VBox body = new VBox(12, grid, error);
        body.setPadding(new Insets(16, 18, 8, 18));
        body.setPrefWidth(720);

        Runnable save = () -> {
            if (Text.isBlank(title.getText())) {
                showFormError(error, "Please give the activity a title.");
                title.requestFocus();
                return;
            }
            if (start.getValue() == null) {
                showFormError(error, "Please choose the date.");
                return;
            }
            if (end.getValue() != null && end.getValue().isBefore(start.getValue())) {
                showFormError(error, "The last day cannot be before the first day.");
                return;
            }
            String startText = Text.clean(startTime.getText());
            String endText = Text.clean(endTime.getText());
            if (!startText.isEmpty() && !startText.matches("\\d{1,2}[:.]\\d{2}")) {
                showFormError(error, "Please write times as HH:MM, for example 09:30.");
                startTime.requestFocus();
                return;
            }
            draft.setTitle(Text.clean(title.getText()));
            draft.setGroupId(group.getValue() == null ? 0 : group.getValue().getId());
            draft.setAreaEnum(area.getValue() == null ? Choices.PlanArea.OTHER : area.getValue());
            draft.setDate(Dates.toIso(start.getValue()));
            boolean range = end.getValue() != null && !end.getValue().equals(start.getValue());
            draft.setEndDate(range ? Dates.toIso(end.getValue()) : null);
            draft.setStartTime(startText.replace('.', ':'));
            draft.setEndTime(endText.replace('.', ':'));
            draft.setStatusEnum(status.getValue() == null
                    ? Choices.PlanStatus.PLANNED : status.getValue());
            draft.setObjectives(objectives.getText().trim());
            draft.setDescription(description.getText().trim());
            draft.setSequence(sequence.getText().trim());
            draft.setMaterials(materials.getText().trim());
            draft.setInstructions(instructions.getText().trim());
            draft.setReflection(reflection.getText().trim());
            draft.setTemplate(template.isSelected());
            draft.setRecurring(recurring.isSelected());
            try {
                context.repo().plans().save(draft);
                date = start.getValue();
                context.setSelectedDate(date);
                context.notifyDataChanged();
                dialog.close();
            } catch (RuntimeException ex) {
                Ui.error("Could not save this activity", ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton(creating ? "Save activity" : "Save changes", save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scroller = Ui.scroll(new VBox(body, buttons));
        scroller.setPrefViewportHeight(660);
        showDialog(dialog, scroller);
        Platform.runLater(title::requestFocus);
    }

    // ---------------------------------------------------------------- printing

    private void printWeek() {
        LocalDate start = Dates.startOfWeek(date);
        long groupId = context.currentGroupId();
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetWeeklyPlan(groupId, start);
            }
        });
    }

    private void printDay() {
        long groupId = context.currentGroupId();
        LocalDate day = date;
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetDailyPlan(groupId, day);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private Stage modalStage(String title) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(shell.window());
        dialog.setTitle(title);
        return dialog;
    }

    private void showDialog(Stage dialog, Node content) {
        Scene scene = new Scene((javafx.scene.Parent) content);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setFill(Color.web("#f4f6f8"));
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showFormError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }
}
