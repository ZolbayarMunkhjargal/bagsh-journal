package app.kinderjournal.ui.screen;

import app.kinderjournal.db.AttendanceDao;
import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.Observation;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

/**
 * Class management: create, rename, describe and retire kindergarten classes,
 * and see the level of journal activity in each one.
 *
 * <p>Deleting a class never deletes children: they become unassigned so their
 * history survives, and the teacher can move them to another class.
 */
public final class ClassesScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());
    private long selectedGroupId;

    public ClassesScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.selectedGroupId = context.currentGroupId();
        refresh();
    }

    @Override
    public String title() {
        return "Classes";
    }

    @Override
    public String subtitle() {
        return "Each class is the container for its children and their journal records.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        List<Group> groups = context.repo().groups().listAll();
        if (selectedGroupId == 0 && !groups.isEmpty()) {
            selectedGroupId = groups.get(0).getId();
        }
        Group selected = null;
        for (Group group : groups) {
            if (group.getId() == selectedGroupId) {
                selected = group;
            }
        }

        VBox page = Ui.page();
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                Ui.screenTitle("Your classes"),
                Ui.meta(Text.plural(groups.size(), "class", "classes")),
                Ui.spacer(),
                Ui.primaryButton("New class", this::openNewForm));
        page.getChildren().add(header);

        if (groups.isEmpty()) {
            page.getChildren().add(Ui.emptyState("No classes yet",
                    "Create a class such as \"Sunflower group\" or \"Morning group\". Children, "
                            + "attendance and lesson plans all belong to a class.",
                    Ui.primaryButton("Create the first class", this::openNewForm)));
            root.setContent(page);
            return;
        }

        HBox columns = new HBox(16);
        VBox left = new VBox(8);
        left.setPrefWidth(360);
        left.setMinWidth(300);
        for (Group group : groups) {
            left.getChildren().add(groupRow(group, group == selected));
        }

        VBox right = selected == null
                ? new VBox(Ui.meta("Select a class to see its details."))
                : buildDetail(selected);
        HBox.setHgrow(right, Priority.ALWAYS);

        columns.getChildren().addAll(left, right);
        page.getChildren().add(columns);
        root.setContent(page);
    }

    // ------------------------------------------------------------------- rows

    private Node groupRow(Group group, boolean selected) {
        int children = group.getChildCount();
        String subtitle = Text.joinNonBlank(" \u00b7 ",
                group.subtitle(),
                Text.plural(children, "child", "children"),
                group.isArchived() ? "archived" : "");
        return RecordList.selectableRow(null, group.getName(), subtitle,
                group.getId() == context.currentGroupId() ? "current" : "",
                selected, () -> {
                    selectedGroupId = group.getId();
                    refresh();
                });
    }

    // ----------------------------------------------------------------- detail

    private VBox buildDetail(Group group) {
        Repository repo = context.repo();
        List<Child> children = repo.children().listByGroup(group.getId(), false);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);

        AttendanceDao.Summary summary = repo.attendance().summaryForGroup(group.getId(), from, to);
        List<Observation> recent = repo.observations().listForGroup(group.getId(), from, to, null);
        int plans = repo.plans().listBetween(group.getId(), from, to).size();
        int interviews = repo.interviews().listForGroup(group.getId(), from, to, null).size();

        VBox box = new VBox(16);

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox names = new VBox(2);
        Label name = new Label(group.getName());
        name.getStyleClass().add("screen-title");
        names.getChildren().add(name);
        Label sub = new Label(Text.joinNonBlank(" \u00b7 ", group.subtitle(),
                group.getTeacher().isBlank() ? "" : "Teacher: " + group.getTeacher()));
        sub.getStyleClass().add("meta");
        names.getChildren().add(sub);
        head.getChildren().addAll(names, Ui.spacer());
        if (group.getId() != context.currentGroupId()) {
            head.getChildren().add(Ui.primaryButton("Work on this class",
                    () -> context.setCurrentGroup(group)));
        } else {
            head.getChildren().add(Ui.pill("Current class", "pill-completed"));
        }
        box.getChildren().addAll(head,
                Ui.row(Ui.button("Edit details", () -> openForm(group)),
                        Ui.button(group.isArchived() ? "Restore class" : "Archive class",
                                () -> toggleArchive(group)),
                        Ui.dangerButton("Delete class", () -> deleteGroup(group))));

        // --- children
        VBox childList = new VBox(6);
        if (children.isEmpty()) {
            childList.getChildren().add(Ui.meta("No children in this class yet."));
        } else {
            for (Child child : children) {
                HBox line = new HBox(10);
                line.setAlignment(Pos.CENTER_LEFT);
                line.getChildren().add(Ui.avatar(child, 14));
                Label childName = new Label(child.fullName());
                childName.getStyleClass().add("body-text");
                line.getChildren().add(childName);
                String age = child.ageText();
                if (!age.isEmpty()) {
                    line.getChildren().add(Ui.meta(age));
                }
                line.getChildren().add(Ui.spacer());
                line.getChildren().add(Ui.linkButton("Open profile", () -> {
                    context.setCurrentChild(child);
                    shell.show("children");
                }));
                childList.getChildren().add(line);
            }
        }
        box.getChildren().add(Ui.cardWithHeader("Children  (" + children.size() + ")",
                Ui.linkButton("Manage children \u2192", () -> shell.show("children")), childList));

        // --- activity summary
        VBox stats = new VBox(6,
                Ui.keyValue("Children", String.valueOf(children.size())),
                Ui.keyValue("Attendance records (last 30 days)", String.valueOf(summary.recorded())),
                Ui.keyValue("Attendance rate", summary.rateText()),
                Ui.keyValue("Observations (last 30 days)", String.valueOf(recent.size())),
                Ui.keyValue("Interviews (last 30 days)", String.valueOf(interviews)),
                Ui.keyValue("Activities planned (last 30 days)", String.valueOf(plans)));
        box.getChildren().add(Ui.card("Class activity", stats));

        // --- recent observations
        VBox recentList = new VBox(6);
        if (recent.isEmpty()) {
            recentList.getChildren().add(Ui.meta("No observations for this class in the last 30 days."));
        } else {
            for (Observation observation : recent.subList(0, Math.min(5, recent.size()))) {
                HBox line = new HBox(8);
                line.setAlignment(Pos.CENTER_LEFT);
                Label date = new Label(Dates.display(observation.getDate()));
                date.getStyleClass().add("row-subtitle");
                Label child = new Label(observation.getChildName());
                child.getStyleClass().add("row-title");
                Label preview = new Label(observation.preview(80));
                preview.getStyleClass().add("row-body");
                HBox.setHgrow(preview, Priority.ALWAYS);
                line.getChildren().addAll(date, child,
                        Ui.chipColoured(observation.categoryEnum().label(),
                                observation.categoryEnum().colour()),
                        preview);
                recentList.getChildren().add(line);
            }
        }
        box.getChildren().add(Ui.cardWithHeader("Recent observations", null, recentList));

        return box;
    }

    // ------------------------------------------------------------------- forms

    private void openNewForm() {
        openForm(null);
    }

    private void openForm(Group existing) {
        boolean creating = existing == null;
        Group draft = creating ? new Group() : existing;
        if (creating) {
            draft.setSchoolYear(Repository.schoolYearGuess());
            draft.setTeacher(context.teacherName());
            draft.setSortOrder(context.repo().groups().listAll().size());
        }

        Stage dialog = modalStage(creating ? "New class" : "Edit class");

        TextField name = Ui.textField(draft.getName(), "e.g. Sunflower group", 28);
        TextField ageRange = Ui.textField(draft.getAgeRange(), "e.g. 4\u20135", 12);
        TextField room = Ui.textField(draft.getRoom(), "e.g. Room 2", 16);
        TextField teacher = Ui.textField(draft.getTeacher(), "Your name", 22);
        TextField year = Ui.textField(draft.getSchoolYear(), "e.g. 2025/2026", 12);
        TextArea description = Ui.textArea(draft.getDescription(), 3);
        description.setPromptText("Anything worth remembering about this group");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field("Class name", name), 0, 0, 2, 1);
        grid.add(Ui.field("Age range", ageRange), 0, 1);
        grid.add(Ui.field("Room", room), 1, 1);
        grid.add(Ui.field("Teacher", teacher), 0, 2);
        grid.add(Ui.field("School year", year), 1, 2);
        grid.add(Ui.field("Description", description), 0, 3, 2, 1);

        VBox body = new VBox(12, grid, error);
        body.setPadding(new Insets(16, 18, 8, 18));
        body.setPrefWidth(480);

        Runnable save = () -> {
            String value = Text.clean(name.getText());
            if (value.isEmpty()) {
                showFormError(error, "Please give the class a name.");
                name.requestFocus();
                return;
            }
            Group duplicate = context.repo().groups().findByName(value);
            if (duplicate != null && duplicate.getId() != draft.getId()) {
                showFormError(error, "Another class already uses the name \"" + value + "\".");
                return;
            }
            draft.setName(value);
            draft.setAgeRange(Text.clean(ageRange.getText()));
            draft.setRoom(Text.clean(room.getText()));
            draft.setTeacher(Text.clean(teacher.getText()));
            draft.setSchoolYear(Text.clean(year.getText()));
            draft.setDescription(Text.clean(description.getText()));
            try {
                if (creating) {
                    context.repo().groups().insert(draft);
                    selectedGroupId = draft.getId();
                } else {
                    context.repo().groups().update(draft);
                }
                context.reloadCurrentGroup();
                context.notifyDataChanged();
                dialog.close();
            } catch (RuntimeException ex) {
                Ui.error("Could not save this class", ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton(creating ? "Create class" : "Save changes", save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        showDialog(dialog, new VBox(body, buttons));
        Platform.runLater(name::requestFocus);
    }

    private void toggleArchive(Group group) {
        boolean archiving = !group.isArchived();
        if (archiving && group.getId() == context.currentGroupId()) {
            Ui.warn("This is the class you are working on",
                    "Switch to another class first, then archive this one.");
            return;
        }
        group.setArchived(archiving);
        try {
            context.repo().groups().update(group);
            context.reloadCurrentGroup();
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not archive this class", ex);
        }
    }

    private void deleteGroup(Group group) {
        int children = context.repo().groups().childCount(group.getId());
        if (children > 0) {
            boolean archive = Ui.confirm("Move the children first",
                    group.getName() + " still has " + Text.plural(children, "child", "children")
                            + ". A class can only be deleted when it is empty, so that no child's "
                            + "history is lost. Move them to another class, then delete this one.\n\n"
                            + "You can archive the class instead: it is hidden from the class picker "
                            + "but everything stays.",
                    "Archive instead");
            if (archive) {
                toggleArchive(group);
            }
            return;
        }
        if (!Ui.confirmDestructive("Delete \"" + group.getName() + "\"?",
                "The class will be removed. Its lesson plans stay in the database but are no longer "
                        + "attached to a class. This cannot be undone.",
                "Delete class")) {
            return;
        }
        try {
            context.repo().groups().delete(group.getId());
            selectedGroupId = 0;
            context.reloadCurrentGroup();
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not delete this class", ex);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Stage modalStage(String title) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(shell.window());
        dialog.setTitle(title);
        dialog.setResizable(false);
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
