package app.kinderjournal.ui.screen;

import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.Interview;
import app.kinderjournal.model.Observation;
import app.kinderjournal.model.Photo;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.PhotosPanel;
import app.kinderjournal.ui.RecordList;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Child history: every record about one child in a single chronological view.
 *
 * <p>Instead of opening attendance, observations and interviews separately, the
 * teacher picks a child and a period and sees everything merged, newest first,
 * grouped by month. This is the screen used to prepare for a parent meeting or to
 * write a progress summary.
 */
public final class HistoryScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    private long childId;
    private LocalDate from;
    private LocalDate to;
    private String category = "all";
    private String kindFilter = "all";
    private long focusedRecordId;
    private int monthWindow = 6;

    public HistoryScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.childId = context.currentChildId();
        this.to = LocalDate.now();
        this.from = to.minusMonths(monthWindow);
        refresh();
    }

    @Override
    public String title() {
        return "Child history";
    }

    @Override
    public String subtitle() {
        return "Attendance, observations, interviews and activities woven into one timeline.";
    }

    @Override
    public Node node() {
        return root;
    }

    /** Selects a child to show; used by links from other screens. */
    public void selectChild(Child child) {
        if (child != null) {
            this.childId = child.getId();
        }
    }

    /** Highlights a specific record when arriving from another screen. */
    public void focusObservation(long observationId) {
        this.focusedRecordId = observationId;
    }

    // ------------------------------------------------------------------- build

    @Override
    public void refresh() {
        List<Child> children = context.repo().children().listAll();
        if (childId == 0 && !children.isEmpty()) {
            childId = children.get(0).getId();
        }
        Child child = childId == 0 ? null : context.repo().children().find(childId);

        VBox page = Ui.page();
        page.getChildren().add(buildToolbar(children, child));

        if (child == null) {
            page.getChildren().add(Ui.emptyState("No child selected",
                    "Choose a child above to see everything recorded about them, or add a child "
                            + "first.",
                    Ui.primaryButton("Go to children", () -> shell.show("children"))));
            root.setContent(page);
            return;
        }

        List<HistoryEntry> entries = context.repo().childHistory(childId, from, to,
                kinds(), category.equals("all") ? null : category);

        page.getChildren().add(buildSummaryCard(child, entries));

        if (entries.isEmpty()) {
            String name = Text.isBlank(child.getFirstName()) ? "this child" : child.getFirstName();
            page.getChildren().add(Ui.emptyState("Nothing recorded in this period",
                    "Widen the date range above, or start by writing an observation about "
                            + name + ".",
                    Ui.primaryButton("Add an observation", () -> shell.showAnd("observations",
                            target -> {
                                if (target instanceof ObservationsScreen observations) {
                                    observations.openNewForm(child);
                                }
                            }))));
            root.setContent(page);
            return;
        }

        HistoryEntry focused = null;
        for (HistoryEntry entry : entries) {
            if (focusedRecordId != 0 && entry.recordId() == focusedRecordId) {
                focused = entry;
            }
        }

        VBox timeline = new VBox(6);
        timeline.setPrefWidth(460);
        timeline.setMinWidth(360);
        String currentMonth = "";
        for (HistoryEntry entry : entries) {
            String month = entry.date() == null || entry.date().length() < 7
                    ? "" : entry.date().substring(0, 7);
            if (!month.equals(currentMonth)) {
                currentMonth = month;
                timeline.getChildren().add(monthHeader(month, entries));
            }
            boolean isFocused = focused != null
                    ? entry.recordId() == focused.recordId() && entry.kind() == focused.kind()
                    : false;
            timeline.getChildren().add(entryRow(child, entry, isFocused));
        }

        VBox detail = focused == null
                ? new VBox(Ui.meta("Select an entry on the left to read it in full."))
                : buildDetail(child, focused);
        HBox.setHgrow(detail, Priority.ALWAYS);

        page.getChildren().add(new HBox(16, timeline, detail));
        root.setContent(page);
    }

    // ---------------------------------------------------------------- toolbar

    private Node buildToolbar(List<Child> children, Child child) {
        VBox box = new VBox(10);

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Child> childChoice = new ComboBox<>();
        childChoice.getItems().addAll(children);
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child value) {
                return value == null ? "" : value.fullName()
                        + (value.getGroupName().isEmpty() ? "" : "  \u00b7  " + value.getGroupName());
            }

            @Override
            public Child fromString(String text) {
                return null;
            }
        });
        childChoice.setPrefWidth(300);
        for (Child candidate : children) {
            if (candidate.getId() == childId) {
                childChoice.setValue(candidate);
            }
        }
        childChoice.valueProperty().addListener((observable, previous, next) -> {
            if (next != null) {
                childId = next.getId();
                focusedRecordId = 0;
                context.setCurrentChild(next);
                refresh();
            }
        });

        line.getChildren().addAll(Ui.fieldLabel("Child"), childChoice, Ui.spacer());
        if (child != null) {
            line.getChildren().addAll(
                    Ui.button("Whole profile", () -> {
                        context.setCurrentChild(child);
                        shell.show("children");
                    }),
                    Ui.button("Print history", () -> printHistory(child)));
        }
        box.getChildren().add(line);

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);

        DatePicker fromPicker = Ui.datePicker(from);
        DatePicker toPicker = Ui.datePicker(to);
        fromPicker.valueProperty().addListener((observable, previous, next) -> {
            from = next;
            refresh();
        });
        toPicker.valueProperty().addListener((observable, previous, next) -> {
            to = next;
            refresh();
        });

        ComboBox<String> categoryChoice = new ComboBox<>();
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("all", "All categories");
        categories.putAll(Choices.observationLabelMap());
        categoryChoice.getItems().addAll(categories.keySet());
        categoryChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : categories.getOrDefault(id, id);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        categoryChoice.setValue(category);
        categoryChoice.setPrefWidth(210);
        categoryChoice.valueProperty().addListener((observable, previous, next) -> {
            category = next == null ? "all" : next;
            refresh();
        });

        filters.getChildren().addAll(
                Ui.fieldLabel("From"), fromPicker, Ui.fieldLabel("To"), toPicker,
                Ui.fieldLabel("Category"), categoryChoice,
                Ui.button("Last 3 months", () -> setRange(3)),
                Ui.button("Last 6 months", () -> setRange(6)),
                Ui.button("This year", () -> {
                    from = LocalDate.of(LocalDate.now().getYear(), 1, 1);
                    to = LocalDate.now();
                    refresh();
                }));
        box.getChildren().add(filters);

        HBox types = new HBox();
        types.getStyleClass().add("segmented");
        types.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.control.ToggleGroup typeGroup = new javafx.scene.control.ToggleGroup();
        String[] options = {"all", HistoryEntry.Kind.ATTENDANCE.id(), HistoryEntry.Kind.OBSERVATION.id(),
                HistoryEntry.Kind.INTERVIEW.id(), HistoryEntry.Kind.PLAN.id()};
        String[] labels = {"Everything", "Attendance", "Observations", "Interviews", "Activities"};
        for (int i = 0; i < options.length; i++) {
            javafx.scene.control.ToggleButton button =
                    new javafx.scene.control.ToggleButton(labels[i]);
            button.setToggleGroup(typeGroup);
            button.setSelected(kindFilter.equals(options[i]));
            final String value = options[i];
            button.setOnAction(event -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    return;
                }
                kindFilter = value;
                focusedRecordId = 0;
                refresh();
            });
            types.getChildren().add(button);
        }
        box.getChildren().add(Ui.row(Ui.fieldLabel("Show"), types));
        return box;
    }

    private void setRange(int months) {
        to = LocalDate.now();
        from = to.minusMonths(months);
        monthWindow = months;
        refresh();
    }

    private List<HistoryEntry.Kind> kinds() {
        if (kindFilter.equals("all")) {
            return null;
        }
        return List.of(HistoryEntry.Kind.fromId(kindFilter));
    }

    // ---------------------------------------------------------------- summary

    private Node buildSummaryCard(Child child, List<HistoryEntry> entries) {
        Map<HistoryEntry.Kind, Integer> counts = new EnumMap<>(HistoryEntry.Kind.class);
        for (HistoryEntry entry : entries) {
            counts.merge(entry.kind(), 1, Integer::sum);
        }

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().add(Ui.avatar(child, 26));
        VBox names = new VBox(2);
        Label name = new Label(child.fullName());
        name.getStyleClass().add("screen-title");
        Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                child.getGroupName().isEmpty() ? "No class" : child.getGroupName(),
                child.birthSummary(),
                Dates.display(Dates.toIso(from)) + " to " + Dates.display(Dates.toIso(to))));
        meta.getStyleClass().add("meta");
        names.getChildren().addAll(name, meta);
        head.getChildren().addAll(names, Ui.spacer());
        for (HistoryEntry.Kind kind : HistoryEntry.Kind.values()) {
            int value = counts.getOrDefault(kind, 0);
            if (value > 0) {
                Label chip = Ui.chipColoured(kind.label() + "  " + value, kind.colour());
                head.getChildren().add(chip);
            }
        }
        return Ui.card(null, head);
    }

    // ---------------------------------------------------------------- timeline

    private Node monthHeader(String month, List<HistoryEntry> entries) {
        int count = 0;
        for (HistoryEntry entry : entries) {
            if (entry.date() != null && entry.date().startsWith(month)) {
                count++;
            }
        }
        YearMonth yearMonth = month.isEmpty() ? YearMonth.now() : YearMonth.parse(month);
        String label = yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + yearMonth.getYear();
        HBox box = new HBox(8,
                Ui.sectionTitle(label),
                Ui.pill(count + (count == 1 ? " record" : " records"), "pill-count"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 0, 2, 2));
        return box;
    }

    private Node entryRow(Child child, HistoryEntry entry, boolean focused) {
        Node row = RecordList.timelineEntry(entry, selected -> {
            focusedRecordId = selected.recordId();
            refresh();
        }, null, null, true, true);
        if (focused) {
            row.getStyleClass().add("list-row-selected");
        }
        return row;
    }

    // ------------------------------------------------------------------ detail

    private VBox buildDetail(Child child, HistoryEntry entry) {
        VBox box = new VBox(12);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().add(Ui.chipColoured(entry.kind().label(), entry.badgeColour()));
        Label date = new Label(Dates.displayLong(entry.date()));
        date.getStyleClass().add("screen-title");
        head.getChildren().add(date);
        head.getChildren().add(Ui.spacer());
        box.getChildren().add(head);

        if (!Text.isBlank(entry.title())) {
            Label title = new Label(entry.title());
            title.getStyleClass().add("row-title");
            title.setWrapText(true);
            box.getChildren().add(title);
        }
        if (!Text.isBlank(entry.summary())) {
            Label summary = new Label(entry.summary());
            summary.getStyleClass().add("body-text");
            summary.setWrapText(true);
            box.getChildren().add(summary);
        }

        switch (entry.kind()) {
            case OBSERVATION -> box.getChildren().add(observationDetail(child, entry));
            case INTERVIEW -> box.getChildren().add(interviewDetail(child, entry));
            case ATTENDANCE -> box.getChildren().add(attendanceDetail(child, entry));
            case PLAN -> box.getChildren().add(planDetail(entry));
            default -> {
                VBox text = new VBox(Ui.textBlock(entry.detail()));
                text.getStyleClass().add("detail-pane");
                box.getChildren().add(text);
            }
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(
                Ui.button("Open profile", () -> {
                    context.setCurrentChild(child);
                    shell.show("children");
                }),
                Ui.button("Write a new observation", () -> shell.showAnd("observations", target -> {
                    if (target instanceof ObservationsScreen observations) {
                        observations.openNewForm(child);
                    }
                })),
                focusedRecordId != 0 ? Ui.button("Clear selection", () -> {
                    focusedRecordId = 0;
                    refresh();
                }) : new Label());
        box.getChildren().add(actions);
        return box;
    }

    private Node observationDetail(Child child, HistoryEntry entry) {
        Observation observation = context.repo().observations().find(entry.recordId());
        if (observation == null) {
            return Ui.meta("This observation has since been deleted.");
        }
        VBox box = new VBox(10);
        VBox text = new VBox(Ui.textBlock(observation.getBody()));
        text.getStyleClass().add("detail-pane");
        text.setPadding(new Insets(12, 14, 12, 14));
        box.getChildren().add(text);

        box.getChildren().add(Ui.row(
                Ui.meta(Text.joinNonBlank(" \u00b7 ", observation.getContext(),
                        observation.getAuthor().isEmpty() ? "" : "by " + observation.getAuthor(),
                        observation.isConfidential() ? "confidential" : "")),
                Ui.spacer(),
                Ui.button("Edit observation", () -> shell.showAnd("observations", target -> {
                    if (target instanceof ObservationsScreen observations) {
                        observations.openNewForm(null);
                    }
                }))));

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.OBSERVATION,
                observation.getId(), child.getId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card("Photos", photos.node()));
        return box;
    }

    private Node interviewDetail(Child child, HistoryEntry entry) {
        Interview interview = context.repo().interviews().find(entry.recordId());
        if (interview == null) {
            return Ui.meta("This interview note has since been deleted.");
        }
        VBox box = new VBox(10);
        VBox text = new VBox(Ui.textBlock(entry.detail()));
        text.getStyleClass().add("detail-pane");
        text.setPadding(new Insets(12, 14, 12, 14));
        box.getChildren().add(text);

        if (!interview.isFollowUpDone() && !Text.isBlank(interview.getFollowUp())) {
            HBox banner = new HBox(10);
            banner.getStyleClass().add(interview.isFollowUpDue() ? "warning-banner" : "info-banner");
            banner.setAlignment(Pos.CENTER_LEFT);
            Label label = new Label("Follow-up: " + interview.getFollowUp()
                    + (Text.isBlank(interview.getFollowUpDate()) ? ""
                    : "  (due " + Dates.display(interview.getFollowUpDate()) + ")"));
            label.setWrapText(true);
            HBox.setHgrow(label, Priority.ALWAYS);
            banner.getChildren().addAll(label, Ui.button("Mark as done", () -> {
                context.repo().interviews().setFollowUpDone(interview.getId(), true);
                context.notifyDataChanged();
            }));
            box.getChildren().add(banner);
        }

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.INTERVIEW,
                interview.getId(), child.getId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card("Photos", photos.node()));
        return box;
    }

    private Node attendanceDetail(Child child, HistoryEntry entry) {
        VBox box = new VBox(8);
        LocalDate date = Dates.fromIso(entry.date());
        var record = date == null ? null : context.repo().attendanceFor(child.getId(), date);
        if (record == null) {
            box.getChildren().add(Ui.meta("No attendance record found for this day."));
            return box;
        }
        box.getChildren().addAll(
                Ui.keyValue("Status", record.statusEnum().label()),
                Ui.keyValue("Reason", record.getReason()),
                Ui.keyValue("Note", record.getNote()),
                Ui.keyValue("Arrival", record.getArrivalTime()),
                Ui.keyValue("Departure", record.getDepartureTime()),
                Ui.keyValue("Recorded", record.getRecordedAt()));
        box.getChildren().add(Ui.button("Open the register for this day", () -> shell.showAnd(
                "attendance", target -> {
                    if (target instanceof AttendanceScreen attendance) {
                        attendance.goToDate(date);
                    }
                })));
        return box;
    }

    private Node planDetail(HistoryEntry entry) {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.meta(entry.summary()));
        VBox text = new VBox(Ui.textBlock(entry.detail()));
        text.getStyleClass().add("detail-pane");
        text.setPadding(new Insets(12, 14, 12, 14));
        box.getChildren().add(text);
        box.getChildren().add(Ui.meta("Activity plans belong to the whole class, so they appear in "
                + "every child's history for the day. Each child's own participation is recorded as "
                + "an observation."));
        box.getChildren().add(Ui.button("Open the weekly plan", () -> shell.show("plans")));
        return box;
    }

    // ---------------------------------------------------------------- printing

    private void printHistory(Child child) {
        Group group = context.repo().groups().find(child.getGroupId());
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetHistory(child, group, from, to);
            }
        });
    }

    /** A compact textual version of the timeline, used by the report builder. */
    public String plainTextSummary(Child child) {
        List<HistoryEntry> entries = context.repo().childHistory(child.getId(), from, to,
                kinds(), category.equals("all") ? null : category);
        StringBuilder sb = new StringBuilder();
        for (HistoryEntry entry : entries) {
            sb.append(Dates.display(entry.date())).append(" \u2014 ")
                    .append(entry.kind().label()).append(": ")
                    .append(entry.summary()).append('\n');
        }
        return sb.toString();
    }
}
