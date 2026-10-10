package app.kinderjournal.ui.screen;

import app.kinderjournal.db.AttendanceDao;
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
import app.kinderjournal.ui.RecordList;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Children: the roster, each child's profile, and every record attached to them.
 *
 * <p>This is the screen teachers spend most time on, so the profile pane offers
 * the profile fields, a quick "add observation" shortcut, the child's photos and
 * a direct link into the full chronological history.
 */
public final class ChildrenScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    private long selectedChildId;
    private String searchText = "";
    private boolean showArchived;
    private boolean showAllClasses;

    public ChildrenScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.selectedChildId = context.currentChildId();
        refresh();
    }

    @Override
    public String title() {
        return "Children";
    }

    @Override
    public String subtitle() {
        return "Profiles, contacts, photos and the records belonging to each child.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        List<Child> children = visibleChildren();
        if (selectedChildId == 0 && !children.isEmpty()) {
            selectedChildId = children.get(0).getId();
        }
        Child selected = null;
        for (Child child : children) {
            if (child.getId() == selectedChildId) {
                selected = child;
            }
        }

        VBox page = Ui.page();
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                Ui.screenTitle("Children"),
                Ui.meta(Text.plural(children.size(), "child", "children")),
                Ui.spacer(),
                Ui.primaryButton("Add a child", this::openNewChildForm));
        page.getChildren().add(header);

        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        TextField search = Ui.textField(searchText, "Search by name\u2026", 18);
        search.textProperty().addListener((observable, previous, next) -> {
            searchText = next == null ? "" : next;
            refresh();
        });
        ComboBox<String> scope = new ComboBox<>();
        scope.getItems().addAll("This class", "All classes", "Unassigned");
        scope.setValue(showAllClasses ? "All classes" : "This class");
        scope.valueProperty().addListener((observable, previous, next) -> {
            showAllClasses = "All classes".equals(next);
            refresh();
        });
        CheckBox archived = new CheckBox("Include archived");
        archived.setSelected(showArchived);
        archived.selectedProperty().addListener((observable, previous, next) -> {
            showArchived = next;
            refresh();
        });
        filterRow.getChildren().addAll(search, scope, archived);
        page.getChildren().add(filterRow);

        if (children.isEmpty()) {
            page.getChildren().add(Ui.emptyState(
                    context.currentGroup() == null ? "No class selected" : "No children found",
                    context.currentGroup() == null
                            ? "Create a class first, then add the children in it."
                            : "Add the children in " + context.currentGroupName()
                            + ", or change the filter above to look at another class.",
                    Ui.primaryButton("Add a child", this::openNewChildForm)));
            root.setContent(page);
            return;
        }

        VBox list = new VBox(6);
        list.setPrefWidth(330);
        list.setMinWidth(280);
        for (Child child : children) {
            list.getChildren().add(childRow(child, child.getId() == selectedChildId));
        }

        VBox detail = selected == null
                ? new VBox(Ui.meta("Select a child to see their profile."))
                : buildProfile(selected);
        HBox.setHgrow(detail, Priority.ALWAYS);
        HBox.setHgrow(list, Priority.NEVER);

        HBox columns = new HBox(16, list, detail);
        page.getChildren().add(columns);
        root.setContent(page);
    }

    // ------------------------------------------------------------------ roster

    private List<Child> visibleChildren() {
        Repository repo = context.repo();
        List<Child> base;
        if (showAllClasses) {
            base = repo.children().listByGroup(0, showArchived);
        } else if (context.currentGroupId() > 0) {
            base = repo.children().listByGroup(context.currentGroupId(), showArchived);
        } else {
            base = repo.children().listByGroup(-1, showArchived);
        }
        if (Text.isBlank(searchText)) {
            return base;
        }
        String needle = Text.lower(searchText.trim());
        List<Child> filtered = new ArrayList<>();
        for (Child child : base) {
            if (Text.lower(child.fullName()).contains(needle)) {
                filtered.add(child);
            }
        }
        return filtered;
    }

    private Node childRow(Child child, boolean selected) {
        String subtitle = Text.joinNonBlank(" \u00b7 ",
                child.ageText(),
                child.getGroupName().isBlank() ? "no class" : child.getGroupName(),
                child.isArchived() ? "archived" : "");
        return RecordList.selectableRow(Ui.avatar(child, 16), child.fullName(), subtitle,
                ReportsScreenSupport.childrenBadge(context, child), selected, () -> {
                    selectedChildId = child.getId();
                    context.setCurrentChild(child);
                    refresh();
                });
    }

    // ----------------------------------------------------------------- profile

    private VBox buildProfile(Child child) {
        Repository repo = context.repo();
        VBox box = new VBox(16);

        // --- identity header
        HBox identity = new HBox(14);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.getChildren().add(Ui.avatar(child, 34));

        VBox names = new VBox(2);
        Label name = new Label(child.fullName());
        name.getStyleClass().add("screen-title");
        names.getChildren().add(name);
        Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                child.getGroupName().isBlank() ? "No class assigned" : child.getGroupName(),
                child.birthSummary(),
                child.getStartDate() == null ? "" : "Started " + Dates.display(child.getStartDate()),
                child.isArchived() ? "Archived" : ""));
        meta.getStyleClass().add("meta");
        meta.setWrapText(true);
        names.getChildren().add(meta);
        if (!Text.isBlank(child.getAllergies())) {
            Label allergy = Ui.pill("Allergies: " + child.getAllergies(), "pill-absent");
            names.getChildren().add(allergy);
        }
        identity.getChildren().addAll(names, Ui.spacer());

        VBox photoActions = new VBox(4,
                Ui.button("Change photo\u2026", () -> chooseProfilePhoto(child)),
                child.getPhotoFile() == null ? new Label() : Ui.linkButton("Remove photo",
                        () -> removeProfilePhoto(child)));
        identity.getChildren().add(photoActions);
        box.getChildren().add(identity);

        // --- main actions
        FlowPane actions = new FlowPane(8, 8);
        actions.getChildren().addAll(
                Ui.primaryButton("Add observation", () -> shell.showAnd("observations",
                        screen -> {
                            if (screen instanceof ObservationsScreen observations) {
                                observations.openNewForm(child);
                            }
                        })),
                Ui.button("Record interview", () -> shell.showAnd("interviews", screen -> {
                    if (screen instanceof InterviewsScreen interviews) {
                        interviews.openNewForm(child);
                    }
                })),
                Ui.button("Full history", () -> {
                    context.setCurrentChild(child);
                    shell.showAnd("history", screen -> {
                        if (screen instanceof HistoryScreen history) {
                            history.selectChild(child);
                        }
                    });
                }),
                Ui.button("Edit profile", () -> openEditForm(child)),
                Ui.button("Move to another class", () -> openMoveDialog(child)),
                Ui.button("Print report", () -> shell.showAnd("reports", screen -> {
                    if (screen instanceof ReportsScreen reports) {
                        reports.presetChild(child);
                    }
                })));
        box.getChildren().add(actions);

        // --- numbers
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(3);
        Repository.ChildSummary summary = repo.summaryFor(child.getId(), from, to);
        AttendanceDao.Summary attendance = summary.attendance();
        FlowPane tiles = new FlowPane(12, 12);
        tiles.getChildren().addAll(
                miniTile(attendance.rateText(), "Attendance rate", "last 3 months"),
                miniTile(String.valueOf(attendance.absent()), "Days absent", "last 3 months"),
                miniTile(String.valueOf(summary.observations()), "Observations", "last 3 months"),
                miniTile(String.valueOf(summary.interviews()), "Interviews", "last 3 months"),
                miniTile(String.valueOf(summary.photos()), "Photos", "on file"));
        box.getChildren().add(tiles);

        // --- contact details
        GridPane fields = Ui.formGrid();
        fields.add(Ui.labelledValue("Date of birth",
                child.getBirthDate() == null ? "" : Dates.display(child.getBirthDate())
                        + (child.ageText().isEmpty() ? "" : "  (" + child.ageText() + ")")), 0, 0);
        fields.add(Ui.labelledValue("First day", child.getStartDate() == null ? ""
                : Dates.display(child.getStartDate())), 1, 0);
        fields.add(Ui.labelledValue("Guardian", Text.joinNonBlank(" \u00b7 ",
                child.getGuardianName(), child.getGuardianRelation())), 0, 1);
        fields.add(Ui.labelledValue("Phone", child.getGuardianPhone()), 1, 1);
        fields.add(Ui.labelledValue("Email", child.getGuardianEmail()), 0, 2);
        fields.add(Ui.labelledValue("Second contact", child.getSecondContact()), 1, 2);
        fields.add(Ui.labelledValue("Address", child.getAddress()), 0, 3, 2, 1);
        fields.add(Ui.labelledValue("Allergies / medical notes", child.getAllergies()), 0, 4, 2, 1);
        fields.add(Ui.labelledValue("Profile notes", child.getNotes()), 0, 5, 2, 1);
        box.getChildren().add(Ui.card("Profile", fields));

        // --- recent records
        List<HistoryEntry> history = repo.childHistory(child.getId(), from, to, null, null);
        VBox recent = new VBox(6);
        if (history.isEmpty()) {
            recent.getChildren().add(Ui.meta("Nothing recorded in the last three months."));
        } else {
            for (HistoryEntry entry : history.subList(0, Math.min(8, history.size()))) {
                recent.getChildren().add(RecordList.timelineEntry(entry, e -> {
                    context.setCurrentChild(child);
                    shell.showAnd("history", screen -> {
                        if (screen instanceof HistoryScreen historyScreen) {
                            historyScreen.selectChild(child);
                        }
                    });
                }, null, null, true, true));
            }
        }
        box.getChildren().add(Ui.cardWithHeader("Recent records", Ui.linkButton("Everything \u2192",
                () -> shell.showAnd("history", screen -> {
                    if (screen instanceof HistoryScreen historyScreen) {
                        historyScreen.selectChild(child);
                    }
                })), recent));

        // --- photos
        box.getChildren().add(photosCard(child));

        // --- danger zone
        HBox dangerous = new HBox(8,
                Ui.button(child.isArchived() ? "Restore child" : "Archive child", () -> {
                    context.repo().children().setArchived(child.getId(), !child.isArchived());
                    context.notifyDataChanged();
                }),
                Ui.dangerButton("Delete child\u2026", () -> deleteChild(child)));
        box.getChildren().add(Ui.card("Archive or remove", dangerous,
                Ui.meta("Archiving keeps every record but hides the child from registers. "
                        + "Deleting removes the profile, all records and all attached photos.")));

        return box;
    }

    private Node miniTile(String value, String label, String hint) {
        VBox box = new VBox(2);
        box.getStyleClass().add("stat-tile");
        box.setPrefWidth(150);
        box.setMinWidth(130);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");
        labelLabel.setWrapText(true);
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("stat-hint");
        box.getChildren().addAll(valueLabel, labelLabel, hintLabel);
        return box;
    }

    // ------------------------------------------------------------------ photos

    private Node photosCard(Child child) {
        List<Photo> photos = context.repo().photos().listForChild(child.getId());
        FlowPane pane = new FlowPane(10, 10);

        for (Photo photo : photos) {
            VBox tile = new VBox(4);
            tile.getStyleClass().add("photo-tile");
            tile.setPrefWidth(158);
            Node image = Ui.imageOrPlaceholder(photo.getFile(), 148, 110);
            tile.getChildren().add(image);
            Label caption = new Label(photo.displayLabel());
            caption.getStyleClass().add("meta");
            caption.setWrapText(true);
            caption.setMaxWidth(148);
            Label where = new Label(photo.getOwnerType().label());
            where.getStyleClass().add("stat-hint");
            HBox buttons = new HBox(2,
                    Ui.linkButton("Caption", () -> editCaption(photo)),
                    Ui.linkButton("Remove", () -> removePhoto(photo)));
            tile.getChildren().addAll(caption, where, buttons);
            pane.getChildren().add(tile);
        }

        VBox wrapper = new VBox(10);
        if (photos.isEmpty()) {
            wrapper.getChildren().add(Ui.meta("No photos attached to this child yet."));
        } else {
            wrapper.getChildren().add(pane);
        }
        wrapper.getChildren().add(Ui.row(
                Ui.button("Attach a photo\u2026", () -> attachPhoto(child)),
                Ui.meta("Photos are copied into the local data folder and never uploaded.")));
        return Ui.cardWithHeader("Photos  (" + photos.size() + ")", null, wrapper);
    }

    private void attachPhoto(Child child) {
        File file = chooseImage("Attach a photo");
        if (file == null) {
            return;
        }
        String caption = Ui.prompt("Photo caption",
                "Describe the photo (optional)", "").orElse(null);
        if (caption == null) {
            return;
        }
        try {
            context.repo().photos().add(Photo.OwnerType.CHILD, child.getId(), child.getId(),
                    file.toPath(), caption);
            context.notifyDataChanged();
        } catch (Exception ex) {
            Ui.error("Could not attach this photo", ex);
        }
    }

    private void editCaption(Photo photo) {
        String value = Ui.prompt("Photo caption", "Caption", photo.getCaption()).orElse(null);
        if (value == null) {
            return;
        }
        context.repo().photos().updateCaption(photo.getId(), value);
        context.notifyDataChanged();
    }

    private void removePhoto(Photo photo) {
        if (!Ui.confirmDestructive("Remove this photo?",
                "The picture will be deleted from the local photos folder. This cannot be undone.",
                "Remove photo")) {
            return;
        }
        context.repo().photos().delete(photo.getId());
        context.notifyDataChanged();
    }

    private File chooseImage(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        return chooser.showOpenDialog(shell.window());
    }

    // ---------------------------------------------------------- profile photo

    private void chooseProfilePhoto(Child child) {
        File file = chooseImage("Choose a profile photo");
        if (file == null) {
            return;
        }
        try {
            context.repo().setProfilePhoto(child.getId(), file.toPath());
            context.notifyDataChanged();
        } catch (Exception ex) {
            Ui.error("Could not set the profile photo", ex);
        }
    }

    private void removeProfilePhoto(Child child) {
        if (!Ui.confirmDestructive("Remove the profile photo?",
                "The picture file will be deleted. The child's initials will be shown instead.",
                "Remove photo")) {
            return;
        }
        try {
            context.repo().children().setPhotoFile(child.getId(), null);
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not remove the profile photo", ex);
        }
    }

    // ------------------------------------------------------------------- forms

    /** Opens the "new child" form; used by the dashboard shortcut too. */
    public void openNewChildForm() {
        openForm(null);
    }

    private void openEditForm(Child child) {
        openForm(child);
    }

    private void openForm(Child existing) {
        boolean creating = existing == null;
        Child draft = creating ? new Child() : existing;
        if (creating) {
            draft.setGroupId(context.currentGroupId());
            draft.setStartDate(Dates.toIso(LocalDate.now()));
        }

        Stage dialog = modalStage(creating ? "Add a child" : "Edit profile");
        dialog.setResizable(true);

        TextField firstName = Ui.textField(draft.getFirstName(), "First name", 18);
        TextField lastName = Ui.textField(draft.getLastName(), "Last name", 18);
        DatePicker birth = Ui.datePicker(draft.birthLocalDate());
        TextField gender = Ui.textField(draft.getGender(), "optional", 12);
        DatePicker started = Ui.datePicker(Dates.fromIso(draft.getStartDate()));

        ComboBox<Group> group = new ComboBox<>();
        List<Group> groups = context.repo().groups().listActive();
        group.getItems().add(null);
        group.getItems().addAll(groups);
        group.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group value) {
                return value == null ? "(no class)" : value.getName();
            }

            @Override
            public Group fromString(String text) {
                return null;
            }
        });
        group.setValue(null);
        for (Group candidate : groups) {
            if (candidate.getId() == draft.getGroupId()) {
                group.setValue(candidate);
            }
        }
        group.setPrefWidth(220);

        TextField guardianName = Ui.textField(draft.getGuardianName(), "Parent or guardian", 22);
        TextField relation = Ui.textField(draft.getGuardianRelation(), "e.g. mother", 14);
        TextField phone = Ui.textField(draft.getGuardianPhone(), "Phone", 18);
        TextField email = Ui.textField(draft.getGuardianEmail(), "Email", 22);
        TextField secondContact = Ui.textField(draft.getSecondContact(), "Name and phone", 26);
        TextField address = Ui.textField(draft.getAddress(), "Home address", 26);
        TextField allergies = Ui.textField(draft.getAllergies(), "Allergies or medical notes", 26);
        TextArea notes = Ui.textArea(draft.getNotes(), 4);
        notes.setPromptText("Anything else worth remembering about this child");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field("First name", firstName), 0, 0);
        grid.add(Ui.field("Last name", lastName), 1, 0);
        grid.add(Ui.field("Date of birth", birth), 0, 1);
        grid.add(Ui.field("Gender (optional)", gender), 1, 1);
        grid.add(Ui.field("Class", group), 0, 2);
        grid.add(Ui.field("First day", started), 1, 2);
        grid.add(Ui.field("Parent / guardian", guardianName), 0, 3);
        grid.add(Ui.field("Relationship", relation), 1, 3);
        grid.add(Ui.field("Phone", phone), 0, 4);
        grid.add(Ui.field("Email", email), 1, 4);
        grid.add(Ui.field("Second contact", secondContact), 0, 5, 2, 1);
        grid.add(Ui.field("Address", address), 0, 6, 2, 1);
        grid.add(Ui.field("Allergies / medical", allergies), 0, 7, 2, 1);
        grid.add(Ui.field("Notes", notes), 0, 8, 2, 1);

        VBox body = new VBox(12, grid, error);
        body.setPadding(new Insets(16, 18, 8, 18));
        body.setPrefWidth(620);

        Runnable save = () -> {
            String first = Text.clean(firstName.getText());
            String last = Text.clean(lastName.getText());
            if (first.isEmpty() && last.isEmpty()) {
                showFormError(error, "Please enter at least a first or last name.");
                firstName.requestFocus();
                return;
            }
            if (birth.getValue() != null && birth.getValue().isAfter(LocalDate.now())) {
                showFormError(error, "The date of birth cannot be in the future.");
                return;
            }
            draft.setFirstName(first);
            draft.setLastName(last);
            draft.setBirthDate(Dates.toIso(birth.getValue()));
            draft.setGender(Text.clean(gender.getText()));
            draft.setGroupId(group.getValue() == null ? 0 : group.getValue().getId());
            draft.setStartDate(Dates.toIso(started.getValue()));
            draft.setGuardianName(Text.clean(guardianName.getText()));
            draft.setGuardianRelation(Text.clean(relation.getText()));
            draft.setGuardianPhone(Text.clean(phone.getText()));
            draft.setGuardianEmail(Text.clean(email.getText()));
            draft.setSecondContact(Text.clean(secondContact.getText()));
            draft.setAddress(Text.clean(address.getText()));
            draft.setAllergies(Text.clean(allergies.getText()));
            draft.setNotes(Text.clean(notes.getText()));
            try {
                context.repo().children().save(draft);
                selectedChildId = draft.getId();
                context.setCurrentChild(draft);
                context.notifyDataChanged();
                dialog.close();
            } catch (RuntimeException ex) {
                Ui.error("Could not save this profile", ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton(creating ? "Add child" : "Save profile", save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scroller = Ui.scroll(new VBox(body, buttons));
        scroller.setPrefViewportHeight(600);
        showDialog(dialog, scroller);
        Platform.runLater(firstName::requestFocus);
    }

    private void openMoveDialog(Child child) {
        List<Group> groups = context.repo().groups().listActive();
        if (groups.isEmpty()) {
            Ui.warn("No classes available", "Create a class first, then move the child into it.");
            return;
        }
        Stage dialog = modalStage("Move " + child.fullName());
        ComboBox<Group> combo = new ComboBox<>();
        combo.getItems().addAll(groups);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group value) {
                return value == null ? "" : value.getName();
            }

            @Override
            public Group fromString(String text) {
                return null;
            }
        });
        for (Group group : groups) {
            if (group.getId() == child.getGroupId()) {
                combo.setValue(group);
            }
        }
        combo.setPrefWidth(260);

        Label explanation = Ui.meta("Attendance, observations and interviews already recorded keep "
                + "the class they were written under, so past registers and reports stay accurate.");
        explanation.setWrapText(true);
        explanation.setMaxWidth(400);

        VBox body = new VBox(12, Ui.field("Move to class", combo), explanation);
        body.setPadding(new Insets(16, 18, 16, 18));
        body.setPrefWidth(440);

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton("Move child", () -> {
                    if (combo.getValue() == null) {
                        return;
                    }
                    context.repo().children().moveToGroup(child.getId(), combo.getValue().getId());
                    context.notifyDataChanged();
                    dialog.close();
                }));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        showDialog(dialog, new VBox(body, buttons));
    }

    private void deleteChild(Child child) {
        int records = context.repo().childHistory(child.getId(), null, null, null, null).size();
        if (!Ui.confirmDestructive("Delete " + child.fullName() + "?",
                "This removes the profile plus " + Text.plural(records, "record", "records")
                        + " (attendance, observations, interviews) and all attached photos.\n\n"
                        + "It cannot be undone. Consider archiving the child instead so the history "
                        + "is kept for your records.",
                "Delete permanently")) {
            return;
        }
        try {
            context.repo().deleteChildCompletely(child.getId());
            selectedChildId = 0;
            context.setCurrentChild(null);
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not delete this profile", ex);
        }
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

    /** Small helper so the roster can show a compact attendance badge. */
    static final class ReportsScreenSupport {
        private ReportsScreenSupport() {
        }

        static String childrenBadge(AppContext context, Child child) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);
            AttendanceDao.Summary summary = context.repo().attendance()
                    .summaryForChild(child.getId(), from, to);
            if (summary.recorded() == 0) {
                return "";
            }
            return summary.rateText();
        }
    }
}
