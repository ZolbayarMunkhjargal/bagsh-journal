package app.kinderjournal.ui.screen;

import app.kinderjournal.db.AttendanceDao;
import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Messages;
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
        return Messages.get("classes.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("classes.subtitle");
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

        String classCount = Messages.get(groups.size() == 1 ? "count.class" : "count.classes",
                groups.size());
        VBox page = Ui.page();
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                Ui.screenTitle(Messages.get("classes.heading")),
                Ui.meta(classCount),
                Ui.spacer(),
                Ui.primaryButton(Messages.get("classes.new"), this::openNewForm));
        page.getChildren().add(header);

        if (groups.isEmpty()) {
            page.getChildren().add(Ui.emptyState(Messages.get("classes.empty.title"),
                    Messages.get("classes.empty.body"),
                    Ui.primaryButton(Messages.get("classes.empty.action"), this::openNewForm)));
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
                ? new VBox(Ui.meta(Messages.get("classes.selectPrompt")))
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
                Messages.get(children == 1 ? "count.child" : "count.children", children),
                group.isArchived() ? Messages.get("classes.archived") : "");
        return RecordList.selectableRow(null, group.getName(), subtitle,
                group.getId() == context.currentGroupId() ? Messages.get("classes.current") : "",
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
                group.getTeacher().isBlank() ? ""
                        : Messages.get("classForm.teacher") + ": " + group.getTeacher()));
        sub.getStyleClass().add("meta");
        names.getChildren().add(sub);
        head.getChildren().addAll(names, Ui.spacer());
        if (group.getId() != context.currentGroupId()) {
            head.getChildren().add(Ui.primaryButton(Messages.get("classes.workOnThis"),
                    () -> context.setCurrentGroup(group)));
        } else {
            head.getChildren().add(Ui.pill(Messages.get("classes.currentBadge"), "pill-completed"));
        }
        box.getChildren().addAll(head,
                Ui.row(Ui.button(Messages.get("classes.editDetails"), () -> openForm(group)),
                        Ui.button(group.isArchived() ? Messages.get("classes.restore")
                                : Messages.get("classes.archive"),
                                () -> toggleArchive(group)),
                        Ui.dangerButton(Messages.get("classes.delete"), () -> deleteGroup(group))));

        // --- children
        VBox childList = new VBox(6);
        if (children.isEmpty()) {
            childList.getChildren().add(Ui.meta(Messages.get("classes.noChildren")));
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
                line.getChildren().add(Ui.linkButton(Messages.get("button.openProfile"), () -> {
                    context.setCurrentChild(child);
                    shell.show("children");
                }));
                childList.getChildren().add(line);
            }
        }
        box.getChildren().add(Ui.cardWithHeader(
                Messages.get("classes.childrenHeading", children.size()),
                Ui.linkButton(Messages.get("classes.manageChildren"), () -> shell.show("children")),
                childList));

        // --- activity summary
        VBox stats = new VBox(6,
                Ui.keyValue(Messages.get("classes.activity.children"), String.valueOf(children.size())),
                Ui.keyValue(Messages.get("classes.activity.attendanceRecords"),
                        String.valueOf(summary.recorded())),
                Ui.keyValue(Messages.get("classes.activity.attendanceRate"), summary.rateText()),
                Ui.keyValue(Messages.get("classes.activity.observations"),
                        String.valueOf(recent.size())),
                Ui.keyValue(Messages.get("classes.activity.interviews"), String.valueOf(interviews)),
                Ui.keyValue(Messages.get("classes.activity.plans"), String.valueOf(plans)));
        box.getChildren().add(Ui.card(Messages.get("classes.activity"), stats));

        // --- recent observations
        VBox recentList = new VBox(6);
        if (recent.isEmpty()) {
            recentList.getChildren().add(Ui.meta(Messages.get("classes.noRecentObservations")));
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
        box.getChildren().add(Ui.cardWithHeader(Messages.get("classes.recentObservations"), null,
                recentList));

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

        Stage dialog = modalStage(creating
                ? Messages.get("classForm.newTitle")
                : Messages.get("classForm.editTitle"));

        TextField name = Ui.textField(draft.getName(), Messages.get("classForm.nameHint"), 28);
        TextField ageRange = Ui.textField(draft.getAgeRange(),
                Messages.get("classForm.ageRangeHint"), 12);
        TextField room = Ui.textField(draft.getRoom(), Messages.get("classForm.roomHint"), 16);
        TextField teacher = Ui.textField(draft.getTeacher(), Messages.get("classForm.teacherHint"), 22);
        TextField year = Ui.textField(draft.getSchoolYear(),
                Messages.get("classForm.schoolYearHint"), 12);
        TextArea description = Ui.textArea(draft.getDescription(), 3);
        description.setPromptText(Messages.get("classForm.descriptionHint"));

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field(Messages.get("classForm.name"), name), 0, 0, 2, 1);
        grid.add(Ui.field(Messages.get("classForm.ageRange"), ageRange), 0, 1);
        grid.add(Ui.field(Messages.get("classForm.room"), room), 1, 1);
        grid.add(Ui.field(Messages.get("classForm.teacher"), teacher), 0, 2);
        grid.add(Ui.field(Messages.get("classForm.schoolYear"), year), 1, 2);
        grid.add(Ui.field(Messages.get("classForm.description"), description), 0, 3, 2, 1);

        VBox body = new VBox(12, grid, error);
        body.setPadding(new Insets(16, 18, 8, 18));
        body.setPrefWidth(480);

        Runnable save = () -> {
            String value = Text.clean(name.getText());
            if (value.isEmpty()) {
                showFormError(error, Messages.get("classForm.error.noName"));
                name.requestFocus();
                return;
            }
            Group duplicate = context.repo().groups().findByName(value);
            if (duplicate != null && duplicate.getId() != draft.getId()) {
                showFormError(error, Messages.get("classForm.error.duplicate", value));
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
                Ui.error(Messages.get("classForm.error.save"), ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button(Messages.get("common.cancel"), dialog::close),
                Ui.primaryButton(creating ? Messages.get("classForm.save.new")
                        : Messages.get("classForm.save.edit"), save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        showDialog(dialog, new VBox(body, buttons));
        Platform.runLater(name::requestFocus);
    }

    private void toggleArchive(Group group) {
        boolean archiving = !group.isArchived();
        if (archiving && group.getId() == context.currentGroupId()) {
            Ui.warn(Messages.get("classArchive.inUseTitle"), Messages.get("classArchive.inUseBody"));
            return;
        }
        group.setArchived(archiving);
        try {
            context.repo().groups().update(group);
            context.reloadCurrentGroup();
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error(Messages.get("classArchive.error"), ex);
        }
    }

    private void deleteGroup(Group group) {
        int children = context.repo().groups().childCount(group.getId());
        if (children > 0) {
            String childCount = Messages.get(children == 1 ? "count.child" : "count.children",
                    children);
            boolean archive = Ui.confirm(Messages.get("classDelete.blockedTitle"),
                    Messages.get("classDelete.blockedBody", group.getName(), childCount)
                            + "\n\n" + Messages.get("classDelete.blockedBody2"),
                    Messages.get("classDelete.archiveInstead"));
            if (archive) {
                toggleArchive(group);
            }
            return;
        }
        if (!Ui.confirmDestructive(Messages.get("classDelete.title", group.getName()),
                Messages.get("classDelete.body"),
                Messages.get("classDelete.confirm"))) {
            return;
        }
        try {
            context.repo().groups().delete(group.getId());
            selectedGroupId = 0;
            context.reloadCurrentGroup();
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error(Messages.get("classDelete.error"), ex);
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
