package app.kinderjournal.ui.screen;

import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.Interview;
import app.kinderjournal.model.Photo;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.PhotosPanel;
import app.kinderjournal.ui.RecordList;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interview notes: conversations with parents, guardians, children and other
 * professionals, each linked to a child and included in that child's history.
 *
 * <p>Every note can carry a follow-up action with a date, and the screen has a
 * view that shows only the follow-ups that have come due, which is the part most
 * easily lost in a paper journal.
 */
public final class InterviewsScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    private Child filterChild;
    private String filterType = "all";
    private String view = "all";
    private LocalDate filterFrom;
    private LocalDate filterTo;
    private long selectedId;

    public InterviewsScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        refresh();
    }

    @Override
    public String title() {
        return "Interviews";
    }

    @Override
    public String subtitle() {
        return "Parent and child conversations, with conclusions and follow-up actions.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        VBox page = Ui.page();
        page.getChildren().add(buildHeader());

        if (context.currentGroup() == null) {
            page.getChildren().add(Ui.emptyState("No class selected",
                    "Pick or create a class before recording an interview.",
                    Ui.primaryButton("Go to classes", () -> shell.show("classes"))));
            root.setContent(page);
            return;
        }

        List<Interview> interviews = load();
        if (interviews.isEmpty()) {
            page.getChildren().add(Ui.emptyState(
                    view.equals("followup") ? "No follow-ups waiting" : "No interviews recorded",
                    view.equals("followup")
                            ? "Every follow-up action has been dealt with. Anything new you record "
                              + "with a due date will appear here."
                            : "Record what was discussed in a parent or child conversation so it is "
                              + "available at the next meeting.",
                    Ui.primaryButton("Record an interview", () -> openNewForm(filterChild))));
            root.setContent(page);
            return;
        }

        Interview selected = null;
        for (Interview interview : interviews) {
            if (interview.getId() == selectedId) {
                selected = interview;
            }
        }
        if (selected == null) {
            selected = interviews.get(0);
            selectedId = selected.getId();
        }

        VBox list = new VBox(6);
        list.setPrefWidth(430);
        list.setMinWidth(350);
        for (Interview interview : interviews) {
            list.getChildren().add(interviewRow(interview, interview.getId() == selectedId));
        }

        VBox detail = buildDetail(selected);
        HBox.setHgrow(detail, Priority.ALWAYS);

        page.getChildren().add(new HBox(16, list, detail));
        root.setContent(page);
    }

    // ------------------------------------------------------------------ header

    private Node buildHeader() {
        VBox box = new VBox(10);

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getChildren().addAll(
                Ui.fieldLabel("Class"), MainShell.groupSelector(context),
                Ui.spacer(),
                Ui.primaryButton("Record an interview", () -> openNewForm(filterChild)));
        box.getChildren().add(line);

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Child> childChoice = new ComboBox<>();
        List<Child> children = context.currentGroup() == null
                ? List.of()
                : context.repo().children().listByGroup(context.currentGroupId(), false);
        childChoice.getItems().add(null);
        childChoice.getItems().addAll(children);
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child child) {
                return child == null ? "All children" : child.fullName();
            }

            @Override
            public Child fromString(String text) {
                return null;
            }
        });
        childChoice.setValue(filterChild);
        childChoice.setPrefWidth(190);
        childChoice.valueProperty().addListener((observable, previous, next) -> {
            filterChild = next;
            refresh();
        });

        Map<String, String> types = new LinkedHashMap<>();
        types.put("all", "All types");
        for (Choices.InterviewType type : Choices.InterviewType.values()) {
            types.put(type.id(), type.label());
        }
        ComboBox<String> typeChoice = new ComboBox<>();
        typeChoice.getItems().addAll(types.keySet());
        typeChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : types.getOrDefault(id, id);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        typeChoice.setValue(filterType);
        typeChoice.setPrefWidth(200);
        typeChoice.valueProperty().addListener((observable, previous, next) -> {
            filterType = next == null ? "all" : next;
            refresh();
        });

        DatePicker from = Ui.datePicker(filterFrom);
        DatePicker to = Ui.datePicker(filterTo);
        from.valueProperty().addListener((observable, previous, next) -> {
            filterFrom = next;
            refresh();
        });
        to.valueProperty().addListener((observable, previous, next) -> {
            filterTo = next;
            refresh();
        });

        HBox modes = new HBox();
        modes.getStyleClass().add("segmented");
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        String[][] options = {{"all", "All notes"}, {"followup", "Follow-ups due"}, {"parent", "Parents"}};
        for (String[] option : options) {
            javafx.scene.control.ToggleButton button =
                    new javafx.scene.control.ToggleButton(option[1]);
            button.setToggleGroup(group);
            button.setSelected(view.equals(option[0]));
            button.setOnAction(event -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    return;
                }
                view = option[0];
                refresh();
            });
            modes.getChildren().add(button);
        }

        filters.getChildren().addAll(Ui.fieldLabel("Child"), childChoice,
                Ui.fieldLabel("Type"), typeChoice, modes,
                Ui.fieldLabel("From"), from, Ui.fieldLabel("To"), to,
                Ui.button("Clear", () -> {
                    filterChild = null;
                    filterType = "all";
                    filterFrom = null;
                    filterTo = null;
                    view = "all";
                    refresh();
                }));
        box.getChildren().add(filters);
        return box;
    }

    private List<Interview> load() {
        Group group = context.currentGroup();
        if (group == null) {
            return List.of();
        }
        if (view.equals("followup")) {
            List<Interview> due = context.repo().interviews().listDueFollowUps(30, 200);
            List<Interview> filtered = new ArrayList<>();
            for (Interview interview : due) {
                if (interview.getGroupId() == group.getId()
                        || context.repo().children().groupIdOf(interview.getChildId()) == group.getId()) {
                    filtered.add(interview);
                }
            }
            return filtered;
        }
        String type = view.equals("parent") ? Choices.InterviewType.PARENT.id() : filterType;
        if (filterChild != null) {
            return context.repo().interviews().listForChild(filterChild.getId(),
                    filterFrom, filterTo, type);
        }
        return context.repo().interviews().listForGroup(group.getId(), filterFrom, filterTo, type);
    }

    // -------------------------------------------------------------------- list

    private Node interviewRow(Interview interview, boolean selected) {
        String trailing = interview.isFollowUpDone() || !interview.isFollowUpDue()
                ? "" : "follow-up due";
        String subtitle = Text.joinNonBlank(" \u00b7 ",
                Dates.display(interview.getDate()),
                interview.getChildName(),
                interview.typeEnum().label(),
                interview.modeEnum().label());
        return RecordList.selectableRow(null,
                interview.headline().isEmpty() ? "(no details)" : interview.headline(),
                subtitle,
                trailing,
                selected, () -> {
                    selectedId = interview.getId();
                    refresh();
                });
    }

    // ------------------------------------------------------------------ detail

    private VBox buildDetail(Interview interview) {
        VBox box = new VBox(12);
        Child child = context.repo().children().find(interview.getChildId());

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        if (child != null) {
            head.getChildren().add(Ui.avatar(child, 18));
        }
        VBox titles = new VBox(2);
        Label title = new Label(Text.joinNonBlank(" \u00b7 ",
                interview.typeEnum().label(),
                interview.getChildName()));
        title.getStyleClass().add("screen-title");
        Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                Dates.displayLong(interview.getDate()),
                interview.modeEnum().label(),
                interview.getParticipants().isEmpty() ? "" : interview.getParticipants()));
        meta.getStyleClass().add("meta");
        meta.setWrapText(true);
        titles.getChildren().addAll(title, meta);
        head.getChildren().addAll(titles, Ui.spacer());
        box.getChildren().add(head);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(Ui.button("Edit", () -> openEditForm(interview)));
        if (child != null) {
            actions.getChildren().addAll(
                    Ui.button("Open profile", () -> {
                        context.setCurrentChild(child);
                        shell.show("children");
                    }),
                    Ui.button("See in history", () -> {
                        context.setCurrentChild(child);
                        shell.showAnd("history", target -> {
                            if (target instanceof HistoryScreen history) {
                                history.selectChild(child);
                            }
                        });
                    }));
        }
        actions.getChildren().add(Ui.dangerButton("Delete", () -> delete(interview)));
        box.getChildren().add(actions);

        GridPane fields = Ui.formGrid();
        fields.add(Ui.labelledValue("Topics discussed", interview.getTopics()), 0, 0, 2, 1);
        fields.add(Ui.labelledValue("Participants", interview.getParticipants()), 0, 1);
        fields.add(Ui.labelledValue("How", interview.modeEnum().label()), 1, 1);
        box.getChildren().add(Ui.card("Summary", fields));

        box.getChildren().add(textSection("What was shared", interview.getDiscussed()));
        box.getChildren().add(textSection("Conclusions and teacher's notes", interview.getConclusions()));

        if (!Text.isBlank(interview.getFollowUp())) {
            VBox follow = new VBox(8);
            Label label = new Label(interview.getFollowUp());
            label.setWrapText(true);
            label.getStyleClass().add("body-text");
            follow.getChildren().add(label);
            follow.getChildren().add(Ui.row(
                    Ui.meta(Text.joinNonBlank(" \u00b7 ",
                            interview.getFollowUpDate() == null ? "no date set"
                                    : "due " + Dates.display(interview.getFollowUpDate()),
                            interview.isFollowUpDone() ? "completed" : "outstanding")),
                    Ui.spacer(),
                    interview.isFollowUpDone()
                            ? Ui.button("Reopen", () -> setFollowUpDone(interview, false))
                            : Ui.primaryButton("Mark as done", () -> setFollowUpDone(interview, true))));
            box.getChildren().add(Ui.card("Follow-up action", follow));
        }

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.INTERVIEW,
                interview.getId(), interview.getChildId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card("Photos", photos.node()));
        return box;
    }

    private Node textSection(String heading, String text) {
        VBox wrapper = new VBox(6);
        if (Text.isBlank(text)) {
            wrapper.getChildren().add(Ui.meta("Nothing recorded under this heading."));
        } else {
            VBox pane = new VBox(Ui.textBlock(text));
            pane.getStyleClass().add("detail-pane");
            pane.setPadding(new Insets(12, 14, 12, 14));
            wrapper.getChildren().add(pane);
        }
        return Ui.card(heading, wrapper);
    }

    private void setFollowUpDone(Interview interview, boolean done) {
        context.repo().interviews().setFollowUpDone(interview.getId(), done);
        context.notifyDataChanged();
    }

    // -------------------------------------------------------------------- form

    /** Opens the interview form, optionally pre-selecting a child. */
    public void openNewForm(Child child) {
        openForm(null, child);
    }

    private void openEditForm(Interview existing) {
        openForm(existing, null);
    }

    private void openForm(Interview existing, Child presetChild) {
        boolean creating = existing == null;
        Interview draft = creating ? new Interview() : existing;
        List<Child> children = context.repo().children().listByGroup(context.currentGroupId(), false);
        if (children.isEmpty() && creating) {
            Ui.warn("No children in this class",
                    "Interviews are always linked to a child. Add the child to a class first.");
            return;
        }
        if (creating) {
            draft.setDate(Dates.toIso(context.selectedDate()));
            draft.setTypeEnum(Choices.InterviewType.PARENT);
            draft.setModeEnum(Choices.InterviewMode.IN_PERSON);
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(shell.window());
        dialog.setTitle(creating ? "Record an interview" : "Edit interview");

        ComboBox<Child> childChoice = new ComboBox<>();
        childChoice.getItems().addAll(children);
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child value) {
                return value == null ? "" : value.fullName();
            }

            @Override
            public Child fromString(String text) {
                return null;
            }
        });
        childChoice.setPrefWidth(260);
        Child initial = presetChild;
        if (initial == null && draft.getChildId() > 0) {
            initial = context.repo().children().find(draft.getChildId());
        }
        if (initial == null && context.currentChildId() > 0) {
            initial = context.repo().children().find(context.currentChildId());
        }
        if (initial == null && !children.isEmpty()) {
            initial = children.get(0);
        }
        childChoice.setValue(initial);

        DatePicker date = Ui.datePicker(Dates.fromIso(draft.getDate()));

        ComboBox<Choices.InterviewType> type = new ComboBox<>();
        type.getItems().addAll(Choices.InterviewType.values());
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choices.InterviewType value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Choices.InterviewType fromString(String text) {
                return null;
            }
        });
        type.setValue(draft.typeEnum());
        type.setPrefWidth(220);

        ComboBox<Choices.InterviewMode> mode = new ComboBox<>();
        mode.getItems().addAll(Choices.InterviewMode.values());
        mode.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choices.InterviewMode value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Choices.InterviewMode fromString(String text) {
                return null;
            }
        });
        mode.setValue(draft.modeEnum());
        mode.setPrefWidth(180);

        TextField participants = Ui.textField(draft.getParticipants(),
                "e.g. Maria Berg (mother), class teacher", 30);
        TextField topics = Ui.textField(draft.getTopics(),
                "e.g. settling in, sleep, friendships", 30);
        TextArea discussed = Ui.textArea(draft.getDiscussed(), 6);
        discussed.setPromptText("What the parent or child shared");
        TextArea conclusions = Ui.textArea(draft.getConclusions(), 5);
        conclusions.setPromptText("Your notes and conclusions");
        TextArea followUp = Ui.textArea(draft.getFollowUp(), 3);
        followUp.setPromptText("Anything to do or revisit later");
        DatePicker followUpDate = Ui.datePicker(Dates.fromIso(draft.getFollowUpDate()));
        CheckBox followUpDone = new CheckBox("Already dealt with");
        followUpDone.setSelected(draft.isFollowUpDone());

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field("Child", childChoice), 0, 0);
        grid.add(Ui.field("Date", date), 1, 0);
        grid.add(Ui.field("Type", type), 0, 1);
        grid.add(Ui.field("How it took place", mode), 1, 1);
        grid.add(Ui.field("Participants", participants), 0, 2, 2, 1);
        grid.add(Ui.field("Topics discussed", topics), 0, 3, 2, 1);
        grid.add(Ui.field("What was shared", discussed), 0, 4, 2, 1);
        grid.add(Ui.field("Conclusions", conclusions), 0, 5, 2, 1);
        grid.add(Ui.field("Follow-up action", followUp), 0, 6, 2, 1);
        grid.add(Ui.field("Follow-up date", followUpDate), 0, 7);
        grid.add(followUpDone, 1, 7);

        VBox photoArea = new VBox(8);
        Label photoHint = Ui.meta(creating
                ? "Photos can be attached once the note has been saved."
                : "Add or remove photos for this interview note below.");
        photoArea.getChildren().add(photoHint);
        if (!creating) {
            PhotosPanel panel = new PhotosPanel(context, Photo.OwnerType.INTERVIEW, draft.getId(),
                    draft.getChildId(), shell.window(),
                    photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                    context::notifyDataChanged);
            photoArea.getChildren().add(panel.node());
        }

        VBox bodyBox = new VBox(12, grid, error, Ui.sectionTitle("Photos"), photoArea);
        bodyBox.setPadding(new Insets(16, 18, 8, 18));
        bodyBox.setPrefWidth(700);

        Runnable save = () -> {
            Child chosen = childChoice.getValue();
            if (chosen == null) {
                showFormError(error, "Please choose the child this interview relates to.");
                return;
            }
            if (date.getValue() == null) {
                showFormError(error, "Please choose the date of the interview.");
                return;
            }
            if (Text.isBlank(topics.getText()) && Text.isBlank(discussed.getText())) {
                showFormError(error, "Please record at least the topics or what was discussed.");
                topics.requestFocus();
                return;
            }
            draft.setChildId(chosen.getId());
            draft.setGroupId(chosen.getGroupId());
            draft.setDate(Dates.toIso(date.getValue()));
            draft.setTypeEnum(type.getValue() == null ? Choices.InterviewType.OTHER : type.getValue());
            draft.setModeEnum(mode.getValue() == null
                    ? Choices.InterviewMode.IN_PERSON : mode.getValue());
            draft.setParticipants(Text.clean(participants.getText()));
            draft.setTopics(Text.clean(topics.getText()));
            draft.setDiscussed(discussed.getText().trim());
            draft.setConclusions(conclusions.getText().trim());
            draft.setFollowUp(followUp.getText().trim());
            draft.setFollowUpDate(Dates.toIso(followUpDate.getValue()));
            draft.setFollowUpDone(followUpDone.isSelected());
            try {
                context.repo().interviews().save(draft);
                selectedId = draft.getId();
                context.setCurrentChild(chosen);
                context.notifyDataChanged();
                dialog.close();
            } catch (RuntimeException ex) {
                Ui.error("Could not save this interview", ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton(creating ? "Save interview" : "Save changes", save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scroller = Ui.scroll(new VBox(bodyBox, buttons));
        scroller.setPrefViewportHeight(640);
        Scene scene = new Scene(scroller);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setFill(Color.web("#f4f6f8"));
        dialog.setScene(scene);
        dialog.setOnHidden(event -> refresh());
        dialog.showAndWait();
        Platform.runLater(topics::requestFocus);
    }

    private void delete(Interview interview) {
        if (!Ui.confirmDestructive("Delete this interview note?",
                "The note and any photos attached to it will be removed. This cannot be undone.",
                "Delete note")) {
            return;
        }
        try {
            context.repo().deleteInterview(interview.getId());
            selectedId = 0;
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not delete this interview", ex);
        }
    }

    private void showFormError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }
}
