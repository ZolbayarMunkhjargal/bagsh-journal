package app.kinderjournal.ui.screen;

import app.kinderjournal.db.AttendanceDao;
import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.Interview;
import app.kinderjournal.model.LessonPlan;
import app.kinderjournal.model.Observation;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.RecordList;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen the application opens on: a summary of the selected class, today's
 * attendance, the latest journal entries, and shortcuts to the usual tasks.
 */
public final class DashboardScreen implements Refreshable {

    private static final int RECENT_LIMIT = 6;

    private final AppContext context;
    private final MainShell shell;
    private final VBox root = new VBox(16, Ui.vspacer());
    private LocalDate today = LocalDate.now();

    public DashboardScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        root.setPadding(new javafx.geometry.Insets(20, 24, 28, 24));
        refresh();
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String subtitle() {
        String group = context.currentGroup() == null ? "" : context.currentGroup().getName();
        String teacher = context.teacherName();
        String greeting = Text.isBlank(teacher) ? "" : "Welcome back, " + teacher + ". ";
        return greeting + Dates.displayLong(Dates.toIso(today))
                + (group.isEmpty() ? "" : "  \u00b7  " + group);
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        today = LocalDate.now();
        root.getChildren().clear();

        Group group = context.currentGroup();
        if (group == null) {
            root.getChildren().add(buildNoClassState());
            return;
        }

        Repository repo = context.repo();
        List<Repository.RegisterRow> register = repo.register(group.getId(), today);
        AttendanceDao.Summary summary = repo.attendance().summaryForGroup(group.getId(), today, today);
        boolean registerStarted = register.stream().anyMatch(Repository.RegisterRow::recorded);

        root.getChildren().add(buildHeader(group, registerStarted, register.size()));
        root.getChildren().add(buildTiles(group, register, summary));
        root.getChildren().add(buildQuickActions(group));

        HBox columns = new HBox(16);
        VBox left = new VBox(16, buildAttendanceCard(group, register, summary));
        VBox right = new VBox(16, buildRecentCard(group));
        left.setPrefWidth(430);
        left.setMinWidth(360);
        HBox.setHgrow(right, Priority.ALWAYS);
        columns.getChildren().addAll(left, right);

        root.getChildren().add(columns);

        VBox extras = new VBox(16);
        Node plans = buildUpcomingCard(group);
        Node followUps = buildFollowUpsCard();
        if (plans != null) {
            extras.getChildren().add(plans);
        }
        if (followUps != null) {
            extras.getChildren().add(followUps);
        }
        if (!extras.getChildren().isEmpty()) {
            root.getChildren().add(extras);
        }
    }

    // ------------------------------------------------------------------ header

    private Node buildHeader(Group group, boolean registerStarted, int roster) {
        VBox box = new VBox(10);

        HBox selector = new HBox(10);
        selector.setAlignment(Pos.CENTER_LEFT);
        selector.getChildren().addAll(Ui.fieldLabel("Class"), MainShell.groupSelector(context));
        if (!group.subtitle().isBlank()) {
            selector.getChildren().add(Ui.meta(group.subtitle()));
        }
        box.getChildren().add(selector);

        if (roster == 0) {
            box.getChildren().add(banner("info",
                    "This class has no children yet. Add the first child to start keeping "
                            + "attendance and observations.",
                    "Add a child", () -> shell.showAnd("children", screen -> {
                        if (screen instanceof ChildrenScreen children) {
                            children.openNewChildForm();
                        }
                    })));
        } else if (!registerStarted) {
            box.getChildren().add(banner("warning",
                    "Today's attendance has not been recorded for " + group.getName() + " yet.",
                    "Record attendance", () -> shell.showAnd("attendance", screen -> {
                        if (screen instanceof AttendanceScreen attendance) {
                            attendance.goToDate(today);
                        }
                    })));
        }
        return box;
    }

    private Node banner(String kind, String message, String actionText, Runnable action) {
        HBox box = new HBox(12);
        box.getStyleClass().add(kind.equals("warning") ? "warning-banner" : "info-banner");
        box.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(message);
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        box.getChildren().add(label);
        if (actionText != null) {
            box.getChildren().add(Ui.button(actionText, action));
        }
        return box;
    }

    private Node buildNoClassState() {
        return Ui.page(Ui.emptyState(
                "No class yet",
                "KinderJournal organises everything by class. Create your first class to begin "
                        + "adding children, taking attendance and writing observations.",
                Ui.primaryButton("Create a class", () -> shell.show("classes"))));
    }

    // ------------------------------------------------------------------- tiles

    private Node buildTiles(Group group, List<Repository.RegisterRow> register,
                            AttendanceDao.Summary summary) {
        FlowPane pane = new FlowPane(12, 12);

        int children = register.size();
        pane.getChildren().add(tile(String.valueOf(children), "Children in " + shortName(group),
                children == 1 ? "1 profile" : "profiles listed"));

        long present = register.stream()
                .filter(row -> row.recorded() && row.status().countedAsAttending()).count();
        long absent = register.stream()
                .filter(row -> row.recorded() && row.status() == Choices.AttendanceStatus.ABSENT).count();
        boolean any = register.stream().anyMatch(Repository.RegisterRow::recorded);
        pane.getChildren().add(tile(any ? present + "/" + children : "\u2013",
                "Present today",
                any ? absent + " absent" : "not recorded yet", !any));

        int obsToday = context.repo().observations().countOn(today);
        pane.getChildren().add(tile(String.valueOf(obsToday), "Observations today",
                obsToday == 0 ? "none written yet" : "keep it up", obsToday == 0));

        LocalDate weekEnd = today.plusDays(6);
        List<LessonPlan> week = context.repo().plans().listBetween(group.getId(), today, weekEnd);
        int completed = (int) week.stream()
                .filter(p -> p.statusEnum() == Choices.PlanStatus.COMPLETED).count();
        pane.getChildren().add(tile(String.valueOf(week.size()), "Activities this week",
                week.isEmpty() ? "nothing planned" : completed + " completed so far",
                week.isEmpty()));

        int followUps = context.repo().interviews().listDueFollowUps(7, 50).size();
        pane.getChildren().add(tile(String.valueOf(followUps), "Follow-ups due",
                followUps == 0 ? "nothing outstanding" : "within the next week", followUps == 0));

        return pane;
    }

    private Node tile(String value, String label, String hint) {
        return tile(value, label, hint, false);
    }

    private Node tile(String value, String label, String hint, boolean mutedValue) {
        VBox box = new VBox(2);
        box.getStyleClass().add("stat-tile");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add(mutedValue ? "stat-value-muted" : "stat-value");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");
        labelLabel.setWrapText(true);
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("stat-hint");
        hintLabel.setWrapText(true);
        box.getChildren().addAll(valueLabel, labelLabel, hintLabel);
        return box;
    }

    private String shortName(Group group) {
        String name = group.getName();
        return name.length() > 22 ? name.substring(0, 21) + "\u2026" : name;
    }

    // ----------------------------------------------------------- quick actions

    private Node buildQuickActions(Group group) {
        FlowPane pane = new FlowPane(8, 8);
        pane.getChildren().addAll(
                Ui.primaryButton("Record today's attendance", () -> shell.showAnd("attendance",
                        screen -> {
                            if (screen instanceof AttendanceScreen attendance) {
                                attendance.goToDate(today);
                            }
                        })),
                Ui.button("Add an observation", () -> shell.showAnd("observations", screen -> {
                    if (screen instanceof ObservationsScreen observations) {
                        observations.openNewForm(null);
                    }
                })),
                Ui.button("Record an interview", () -> shell.showAnd("interviews", screen -> {
                    if (screen instanceof InterviewsScreen interviews) {
                        interviews.openNewForm(null);
                    }
                })),
                Ui.button("Plan an activity", () -> shell.showAnd("plans", screen -> {
                    if (screen instanceof PlansScreen plans) {
                        plans.openNewForm(today);
                    }
                })),
                Ui.button("Open a child's history", () -> shell.show("history")),
                Ui.button("Print a report", () -> shell.show("reports")));
        return pane;
    }

    // -------------------------------------------------------- attendance card

    private Node buildAttendanceCard(Group group, List<Repository.RegisterRow> register,
                                     AttendanceDao.Summary summary) {
        List<Node> rows = new ArrayList<>();
        boolean any = register.stream().anyMatch(Repository.RegisterRow::recorded);

        if (register.isEmpty()) {
            rows.add(Ui.meta("No children in this class yet."));
        } else if (!any) {
            rows.add(Ui.meta("Nothing recorded for today. Everyone will start as Present."));
        }

        for (Repository.RegisterRow row : register) {
            if (any && !row.recorded()) {
                continue;
            }
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getChildren().add(Ui.avatar(row.child(), 15));
            Label name = new Label(row.child().fullName());
            name.getStyleClass().add("body-text");
            line.getChildren().add(name);
            line.getChildren().add(Ui.spacer());
            Choices.AttendanceStatus status = row.recorded()
                    ? row.status() : Choices.AttendanceStatus.PRESENT;
            String style = switch (status) {
                case PRESENT -> "pill-present";
                case ABSENT -> "pill-absent";
                case LATE -> "pill-late";
                case EXCUSED -> "pill-excused";
            };
            String text = status.label();
            if (!Text.isBlank(row.attendance().getReason())) {
                text = text + " \u00b7 " + Text.summarise(row.attendance().getReason(), 24);
            }
            line.getChildren().add(Ui.pill(text, style));
            rows.add(line);
        }

        if (any) {
            rows.add(Ui.keyValue("Attendance rate",
                    summary.rateText() + "  (" + summary.attending() + " of " + summary.recorded() + ")"));
        }

        Node openButton = Ui.linkButton("Open the register \u2192", () -> shell.show("attendance"));
        return Ui.cardWithHeader("Today \u00b7 " + Dates.display(Dates.toIso(today)), openButton,
                RecordList.stack(rows));
    }

    // ------------------------------------------------------------ recent card

    private Node buildRecentCard(Group group) {
        List<Child> roster = context.repo().children().listByGroup(group.getId(), false);
        LocalDate since = today.minusDays(14);
        List<Observation> observations = context.repo().observations()
                .listForChildren(roster, since, null, null, RECENT_LIMIT);

        VBox list = new VBox(8);
        if (observations.isEmpty()) {
            list.getChildren().add(Ui.emptyState("No observations in the last two weeks",
                    "Notes you write about individual children appear here so you can pick up "
                            + "where you left off.",
                    Ui.button("Write the first one", () -> shell.showAnd("observations", screen -> {
                        if (screen instanceof ObservationsScreen observationsScreen) {
                            observationsScreen.openNewForm(null);
                        }
                    }))));
        } else {
            for (Observation observation : observations) {
                HistoryEntry entry = new HistoryEntry(observation.getDate(),
                        HistoryEntry.Kind.OBSERVATION, observation.getId(),
                        Text.joinNonBlank(" \u00b7 ", observation.categoryEnum().label(),
                                observation.getContext()),
                        observation.preview(150), observation.getBody(),
                        observation.categoryEnum().colour(), observation.getPhotoCount());
                list.getChildren().add(RecordList.timelineEntry(entry, e -> shell.showAnd("history",
                        screen -> {
                            if (screen instanceof HistoryScreen history) {
                                history.focusObservation(e.recordId());
                            }
                        }), null, null, true, true));
            }
        }

        Node openButton = Ui.linkButton("All observations \u2192", () -> shell.show("observations"));
        return Ui.cardWithHeader("Latest journal entries", openButton, list);
    }

    // ---------------------------------------------------------- upcoming card

    private Node buildUpcomingCard(Group group) {
        List<LessonPlan> plans = context.repo().plans().listUpcoming(group.getId(), today, 5);
        if (plans.isEmpty()) {
            return null;
        }
        VBox list = new VBox(8);
        for (LessonPlan plan : plans) {
            String when = plan.isRange()
                    ? plan.dateRangeLabel()
                    : (plan.getDate().equals(Dates.toIso(today)) ? "Today" : Dates.display(plan.getDate()));
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label(plan.getTitle());
            title.getStyleClass().add("body-text");
            line.getChildren().addAll(title, Ui.spacer());
            line.getChildren().add(Ui.pill(plan.statusEnum().label(),
                    plan.statusEnum() == Choices.PlanStatus.COMPLETED ? "pill-completed" : "pill-planned"));
            VBox text = new VBox(2, line);
            Label meta = new Label(Text.joinNonBlank(" \u00b7 ", when, plan.timeLabel(),
                    plan.areaEnum().label(), plan.getGroupName()));
            meta.getStyleClass().add("meta");
            text.getChildren().add(meta);
            VBox wrapper = new VBox(text);
            wrapper.getStyleClass().add("list-row");
            wrapper.setOnMouseClicked(event -> shell.show("plans"));
            wrapper.setCursor(javafx.scene.Cursor.HAND);
            list.getChildren().add(wrapper);
        }
        Node openButton = Ui.linkButton("Weekly plan \u2192", () -> shell.show("plans"));
        return Ui.cardWithHeader("Coming up in " + group.getName(), openButton, list);
    }

    // -------------------------------------------------------- follow-ups card

    private Node buildFollowUpsCard() {
        List<Interview> due = context.repo().interviews().listDueFollowUps(7, 5);
        if (due.isEmpty()) {
            return null;
        }
        VBox list = new VBox(8);
        for (Interview interview : due) {
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label(Text.summarise(interview.getFollowUp(), 70));
            title.getStyleClass().add("body-text");
            title.setWrapText(true);
            line.getChildren().add(title);
            VBox wrapper = new VBox(2, line);
            Label meta = new Label(Text.joinNonBlank(" \u00b7 ", interview.getChildName(),
                    interview.typeEnum().label(),
                    interview.getFollowUpDate() == null ? "" : "due "
                            + Dates.display(interview.getFollowUpDate())));
            meta.getStyleClass().add("meta");
            wrapper.getChildren().add(meta);
            wrapper.getStyleClass().add("list-row");
            wrapper.setOnMouseClicked(event -> shell.show("interviews"));
            wrapper.setCursor(javafx.scene.Cursor.HAND);
            list.getChildren().add(wrapper);
        }
        return Ui.cardWithHeader("Follow-ups to revisit", null, list);
    }
}
