package app.kinderjournal.ui.screen;

import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Messages;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        return Messages.get("history.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("history.subtitle");
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
            page.getChildren().add(Ui.emptyState(Messages.get("history.noChild.title"),
                    Messages.get("history.noChild.body"),
                    Ui.primaryButton(Messages.get("history.noChild.action"),
                            () -> shell.show("children"))));
            root.setContent(page);
            return;
        }

        List<HistoryEntry> entries = context.repo().childHistory(childId, from, to,
                kinds(), category.equals("all") ? null : category);

        page.getChildren().add(buildSummaryCard(child, entries));

        if (entries.isEmpty()) {
            String name = Text.isBlank(child.getFirstName())
                    ? Messages.get("history.thisChild") : child.getFirstName();
            page.getChildren().add(Ui.emptyState(Messages.get("history.empty.title"),
                    Messages.get("history.empty.body", name),
                    Ui.primaryButton(Messages.get("history.empty.action"),
                            () -> shell.showAnd("observations", target -> {
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
                ? new VBox(Ui.meta(Messages.get("history.selectPrompt")))
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

        line.getChildren().addAll(Ui.fieldLabel(Messages.get("history.child")), childChoice,
                Ui.spacer());
        if (child != null) {
            line.getChildren().addAll(
                    Ui.button(Messages.get("history.wholeProfile"), () -> {
                        context.setCurrentChild(child);
                        shell.show("children");
                    }),
                    Ui.button(Messages.get("history.printHistory"), () -> printHistory(child)));
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
        categories.put("all", Messages.get("common.allObsCategories"));
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
                Ui.fieldLabel(Messages.get("common.from")), fromPicker,
                Ui.fieldLabel(Messages.get("common.to")), toPicker,
                Ui.fieldLabel(Messages.get("common.category")), categoryChoice,
                Ui.button(Messages.get("history.last3"), () -> setRange(3)),
                Ui.button(Messages.get("history.last6"), () -> setRange(6)),
                Ui.button(Messages.get("history.thisYear"), () -> {
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
        String[] labels = {Messages.get("history.show.everything"),
                Messages.get("history.show.attendance"),
                Messages.get("history.show.observations"),
                Messages.get("history.show.interviews"),
                Messages.get("history.show.activities")};
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
        box.getChildren().add(Ui.row(Ui.fieldLabel(Messages.get("history.show.label")), types));
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
                child.getGroupName().isEmpty()
                        ? Messages.get("common.noClass") : child.getGroupName(),
                child.birthSummary(),
                Messages.get("history.range", Dates.display(Dates.toIso(from)),
                        Dates.display(Dates.toIso(to)))));
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
        // Month and year headings follow the interface language, like every other date.
        String label = Dates.monthName(yearMonth.getYear(), yearMonth.getMonthValue());
        HBox box = new HBox(8,
                Ui.sectionTitle(label),
                Ui.pill(Messages.get(count == 1 ? "history.record" : "history.records", count),
                        "pill-count"));
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
                Ui.button(Messages.get("button.openProfile"), () -> {
                    context.setCurrentChild(child);
                    shell.show("children");
                }),
                Ui.button(Messages.get("history.writeObservation"),
                        () -> shell.showAnd("observations", target -> {
                            if (target instanceof ObservationsScreen observations) {
                                observations.openNewForm(child);
                            }
                        })),
                focusedRecordId != 0 ? Ui.button(Messages.get("history.clearSelection"), () -> {
                    focusedRecordId = 0;
                    refresh();
                }) : new Label());
        box.getChildren().add(actions);
        return box;
    }

    private Node observationDetail(Child child, HistoryEntry entry) {
        Observation observation = context.repo().observations().find(entry.recordId());
        if (observation == null) {
            return Ui.meta(Messages.get("history.observationDeleted"));
        }
        VBox box = new VBox(10);
        VBox text = new VBox(Ui.textBlock(observation.getBody()));
        text.getStyleClass().add("detail-pane");
        text.setPadding(new Insets(12, 14, 12, 14));
        box.getChildren().add(text);

        box.getChildren().add(Ui.row(
                Ui.meta(Text.joinNonBlank(" \u00b7 ", observation.getContext(),
                        observation.getAuthor().isEmpty() ? ""
                                : Messages.get("history.recordedBy", observation.getAuthor()),
                        observation.isConfidential()
                                ? Messages.get("observations.confidential") : "")),
                Ui.spacer(),
                Ui.button(Messages.get("observations.editTitle"),
                        () -> shell.showAnd("observations", target -> {
                            if (target instanceof ObservationsScreen observations) {
                                observations.openNewForm(null);
                            }
                        }))));

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.OBSERVATION,
                observation.getId(), child.getId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card(Messages.get("common.photos"), photos.node()));
        return box;
    }

    private Node interviewDetail(Child child, HistoryEntry entry) {
        Interview interview = context.repo().interviews().find(entry.recordId());
        if (interview == null) {
            return Ui.meta(Messages.get("history.interviewDeleted"));
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
            String due = Text.isBlank(interview.getFollowUpDate()) ? ""
                    : "  (" + Messages.get("interviews.followUp.due",
                            Dates.display(interview.getFollowUpDate())) + ")";
            Label label = new Label(Messages.get("history.followUp",
                    interview.getFollowUp() + due));
            label.setWrapText(true);
            HBox.setHgrow(label, Priority.ALWAYS);
            banner.getChildren().addAll(label, Ui.button(Messages.get("history.markDone"), () -> {
                context.repo().interviews().setFollowUpDone(interview.getId(), true);
                context.notifyDataChanged();
            }));
            box.getChildren().add(banner);
        }

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.INTERVIEW,
                interview.getId(), child.getId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card(Messages.get("common.photos"), photos.node()));
        return box;
    }

    private Node attendanceDetail(Child child, HistoryEntry entry) {
        VBox box = new VBox(8);
        LocalDate date = Dates.fromIso(entry.date());
        var record = date == null ? null : context.repo().attendanceFor(child.getId(), date);
        if (record == null) {
            box.getChildren().add(Ui.meta(Messages.get("history.noAttendance")));
            return box;
        }
        box.getChildren().addAll(
                Ui.keyValue(Messages.get("history.status"), record.statusEnum().label()),
                Ui.keyValue(Messages.get("common.reason"), record.getReason()),
                Ui.keyValue(Messages.get("common.note"), record.getNote()),
                Ui.keyValue(Messages.get("history.arrival"), record.getArrivalTime()),
                Ui.keyValue(Messages.get("history.departure"), record.getDepartureTime()),
                Ui.keyValue(Messages.get("history.recorded"), record.getRecordedAt()));
        box.getChildren().add(Ui.button(Messages.get("history.openRegister"), () -> shell.showAnd(
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
        box.getChildren().add(Ui.meta(Messages.get("history.planNote")));
        box.getChildren().add(Ui.button(Messages.get("history.openWeeklyPlan"),
                () -> shell.show("plans")));
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
