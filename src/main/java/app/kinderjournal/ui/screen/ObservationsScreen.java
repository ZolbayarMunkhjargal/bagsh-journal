package app.kinderjournal.ui.screen;

import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.Observation;
import app.kinderjournal.model.Photo;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.PhotosPanel;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily observations: what the teacher noticed about individual children.
 *
 * <p>Entries are filed under a developmental area (or left uncategorised) so that
 * a term's worth of notes can be reviewed by area when preparing a progress
 * report, while remaining a simple dated note at heart.
 */
public final class ObservationsScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());

    private Child filterChild;
    private String filterCategory = "all";
    private LocalDate filterFrom;
    private LocalDate filterTo;
    private long selectedObservationId;

    public ObservationsScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        refresh();
    }

    @Override
    public String title() {
        return "Observations";
    }

    @Override
    public String subtitle() {
        return "Dated notes on development, participation and behaviour, with optional photos.";
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
                    "Pick or create a class, then observations can be written for its children.",
                    Ui.primaryButton("Go to classes", () -> shell.show("classes"))));
            root.setContent(page);
            return;
        }

        List<Observation> observations = load();
        if (observations.isEmpty()) {
            page.getChildren().add(Ui.emptyState("No observations match these filters",
                    "Write a note about something a child did today - a new word, a kind gesture, "
                            + "a skill they practised - or widen the filters above.",
                    Ui.primaryButton("Add an observation", () -> openNewForm(filterChild))));
            page.getChildren().add(buildCategoryTiles());
            root.setContent(page);
            return;
        }

        Observation selected = null;
        for (Observation observation : observations) {
            if (observation.getId() == selectedObservationId) {
                selected = observation;
            }
        }
        if (selected == null) {
            selected = observations.get(0);
            selectedObservationId = selected.getId();
        }

        VBox list = new VBox(6);
        list.setPrefWidth(420);
        list.setMinWidth(340);
        for (Observation observation : observations) {
            list.getChildren().add(observationRow(observation,
                    observation.getId() == selectedObservationId));
        }

        VBox detail = buildDetail(selected);
        HBox.setHgrow(detail, Priority.ALWAYS);

        page.getChildren().add(buildCategoryTiles());
        page.getChildren().add(new HBox(16, list, detail));
        root.setContent(page);
    }

    // ----------------------------------------------------------------- filters

    private Node buildHeader() {
        VBox box = new VBox(10);

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getChildren().addAll(
                Ui.fieldLabel("Class"), MainShell.groupSelector(context),
                Ui.spacer(),
                Ui.primaryButton("Add an observation", () -> openNewForm(filterChild)));
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
        childChoice.setPrefWidth(200);
        childChoice.valueProperty().addListener((observable, previous, next) -> {
            filterChild = next;
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
        categoryChoice.setValue(filterCategory);
        categoryChoice.setPrefWidth(220);
        categoryChoice.valueProperty().addListener((observable, previous, next) -> {
            filterCategory = next == null ? "all" : next;
            refresh();
        });

        DatePicker from = Ui.datePicker(filterFrom);
        DatePicker to = Ui.datePicker(filterTo);
        from.setPromptText("From");
        to.setPromptText("To");
        from.valueProperty().addListener((observable, previous, next) -> {
            filterFrom = next;
            refresh();
        });
        to.valueProperty().addListener((observable, previous, next) -> {
            filterTo = next;
            refresh();
        });

        filters.getChildren().addAll(
                Ui.fieldLabel("Child"), childChoice,
                Ui.fieldLabel("Category"), categoryChoice,
                Ui.fieldLabel("From"), from,
                Ui.fieldLabel("To"), to,
                Ui.button("Clear", () -> {
                    filterChild = null;
                    filterCategory = "all";
                    filterFrom = null;
                    filterTo = null;
                    refresh();
                }));
        box.getChildren().add(filters);

        if (filterFrom != null && filterTo != null && filterFrom.isAfter(filterTo)) {
            box.getChildren().add(Ui.meta("The start of the range is after its end."));
        }
        return box;
    }

    private List<Observation> load() {
        Group group = context.currentGroup();
        if (group == null) {
            return List.of();
        }
        if (filterChild != null) {
            return context.repo().observations().listForChild(filterChild.getId(),
                    filterFrom, filterTo, filterCategory);
        }
        return context.repo().observations().listForGroup(group.getId(),
                filterFrom, filterTo, filterCategory);
    }

    private Node buildCategoryTiles() {
        Group group = context.currentGroup();
        if (group == null) {
            return new VBox();
        }
        List<Child> roster = context.repo().children().listByGroup(group.getId(), false);
        Map<String, Integer> counts = context.repo().observationCountsByCategory(roster);

        javafx.scene.layout.FlowPane pane = new javafx.scene.layout.FlowPane(8, 8);
        for (Choices.ObservationCategory category : Choices.ObservationCategory.values()) {
            int value = counts.getOrDefault(category.id(), 0);
            Label chip = Ui.chipColoured(category.label() + "  " + value, category.colour());
            chip.setCursor(javafx.scene.Cursor.HAND);
            chip.setOnMouseClicked(event -> {
                filterCategory = category.id();
                refresh();
            });
            pane.getChildren().add(chip);
        }
        return pane;
    }

    // -------------------------------------------------------------------- list

    private Node observationRow(Observation observation, boolean selected) {
        String subtitle = Text.joinNonBlank(" \u00b7 ",
                Dates.display(observation.getDate()),
                observation.getChildName(),
                observation.getContext(),
                observation.getPhotoCount() > 0 ? observation.getPhotoCount() + " photo(s)" : "");
        return app.kinderjournal.ui.RecordList.selectableRow(null,
                observation.headline().isEmpty() ? "(no text)" : observation.headline(),
                subtitle,
                observation.categoryEnum().label(), selected, () -> {
                    selectedObservationId = observation.getId();
                    refresh();
                });
    }

    // ------------------------------------------------------------------ detail

    private VBox buildDetail(Observation observation) {
        VBox box = new VBox(12);

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Child child = context.repo().children().find(observation.getChildId());
        if (child != null) {
            head.getChildren().add(Ui.avatar(child, 18));
        }
        VBox titles = new VBox(2);
        Label title = new Label(observation.headline().isEmpty()
                ? "Observation" : observation.headline());
        title.getStyleClass().add("screen-title");
        title.setWrapText(true);
        Label meta = new Label(Text.joinNonBlank(" \u00b7 ",
                observation.getChildName(),
                Dates.displayLong(observation.getDate()),
                observation.getContext(),
                observation.getAuthor().isEmpty() ? "" : "by " + observation.getAuthor()));
        meta.getStyleClass().add("meta");
        meta.setWrapText(true);
        titles.getChildren().addAll(title, meta);
        head.getChildren().addAll(titles, Ui.spacer());
        box.getChildren().add(head);

        HBox tags = new HBox(8);
        tags.setAlignment(Pos.CENTER_LEFT);
        tags.getChildren().add(Ui.chipColoured(observation.categoryEnum().label(),
                observation.categoryEnum().colour()));
        if (observation.isConfidential()) {
            tags.getChildren().add(Ui.pill("Confidential", "pill-absent"));
        }
        tags.getChildren().addAll(Ui.spacer(),
                Ui.button("Edit", () -> openEditForm(observation)),
                Ui.dangerButton("Delete", () -> delete(observation)));
        box.getChildren().add(tags);

        VBox text = new VBox(Ui.textBlock(observation.getBody()));
        text.getStyleClass().add("detail-pane");
        text.setPadding(new Insets(12, 14, 12, 14));
        box.getChildren().add(text);

        if (child != null) {
            HBox childActions = new HBox(8,
                    Ui.button("Open profile", () -> {
                        context.setCurrentChild(child);
                        shell.show("children");
                    }),
                    Ui.button("See full history", () -> {
                        context.setCurrentChild(child);
                        shell.showAnd("history", target -> {
                            if (target instanceof HistoryScreen history) {
                                history.selectChild(child);
                                history.focusObservation(observation.getId());
                            }
                        });
                    }));
            box.getChildren().add(childActions);
        }

        PhotosPanel photos = new PhotosPanel(context, Photo.OwnerType.OBSERVATION,
                observation.getId(), observation.getChildId(), shell.window(),
                photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                context::notifyDataChanged);
        box.getChildren().add(Ui.card("Photos", photos.node()));

        return box;
    }

    // -------------------------------------------------------------------- form

    /** Opens the observation form, optionally for a specific child. */
    public void openNewForm(Child child) {
        openForm(null, child);
    }

    private void openEditForm(Observation existing) {
        openForm(existing, null);
    }

    private void openForm(Observation existing, Child presetChild) {
        boolean creating = existing == null;
        Observation draft = creating ? new Observation() : existing;
        List<Child> children = context.repo().children().listByGroup(context.currentGroupId(), false);
        if (children.isEmpty() && creating) {
            Ui.warn("No children in this class",
                    "Add the child to a class first, then observations can be written about them.");
            return;
        }

        if (creating) {
            draft.setDate(Dates.toIso(context.selectedDate()));
            draft.setCategoryEnum(Choices.ObservationCategory.GENERAL);
            draft.setAuthor(context.teacherName());
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(shell.window());
        dialog.setTitle(creating ? "New observation" : "Edit observation");

        ComboBox<Child> childChoice = new ComboBox<>();
        childChoice.getItems().addAll(children);
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child value) {
                return value == null ? "" : value.fullName() + (value.getGroupName().isEmpty()
                        ? "" : "  (" + value.getGroupName() + ")");
            }

            @Override
            public Child fromString(String text) {
                return null;
            }
        });
        childChoice.setPrefWidth(280);
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

        ComboBox<Choices.ObservationCategory> category = new ComboBox<>();
        category.getItems().addAll(Choices.ObservationCategory.values());
        category.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choices.ObservationCategory value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Choices.ObservationCategory fromString(String text) {
                return null;
            }
        });
        category.setValue(draft.categoryEnum());
        category.setPrefWidth(280);

        TextField title = Ui.textField(draft.getTitle(),
                "Short headline, e.g. \"New words at circle time\"", 30);
        TextField contextField = Ui.textField(draft.getContext(),
                "Where it happened, e.g. outdoor play", 24);
        TextField author = Ui.textField(draft.getAuthor(), "Your name", 18);
        TextArea body = Ui.textArea(draft.getBody(), 9);
        body.setPromptText("What did you notice? Describe what the child did or said, and the "
                + "situation it happened in.");
        javafx.scene.control.CheckBox confidential = new javafx.scene.control.CheckBox(
                "Mark as confidential (kept out of shared reports)");
        confidential.setSelected(draft.isConfidential());

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = Ui.formGrid();
        grid.add(Ui.field("Child", childChoice), 0, 0);
        grid.add(Ui.field("Date", date), 1, 0);
        grid.add(Ui.field("Category", category), 0, 1);
        grid.add(Ui.field("Context", contextField), 1, 1);
        grid.add(Ui.field("Headline (optional)", title), 0, 2, 2, 1);
        grid.add(Ui.field("Observation", body), 0, 3, 2, 1);
        grid.add(Ui.field("Recorded by", author), 0, 4);
        grid.add(confidential, 1, 4);

        Label photoHint = Ui.meta(creating
                ? "Photos can be added once the observation has been saved."
                : "Add or remove photos for this observation below.");
        photoHint.setWrapText(true);

        VBox photoArea = new VBox(8);
        if (!creating) {
            PhotosPanel panel = new PhotosPanel(context, Photo.OwnerType.OBSERVATION,
                    draft.getId(), draft.getChildId(), shell.window(),
                    photo -> PhotosPanel.showPhotoViewer(photo, shell.window()),
                    context::notifyDataChanged);
            photoArea.getChildren().add(panel.node());
        }

        VBox bodyBox = new VBox(12, grid, error, Ui.sectionTitle("Photos"), photoHint, photoArea);
        bodyBox.setPadding(new Insets(16, 18, 8, 18));
        bodyBox.setPrefWidth(660);

        Runnable save = () -> {
            Child chosen = childChoice.getValue();
            if (chosen == null) {
                showFormError(error, "Please choose the child this observation is about.");
                return;
            }
            if (date.getValue() == null) {
                showFormError(error, "Please choose the date of the observation.");
                return;
            }
            if (Text.isBlank(body.getText())) {
                showFormError(error, "Please write what you observed.");
                body.requestFocus();
                return;
            }
            draft.setChildId(chosen.getId());
            draft.setGroupId(chosen.getGroupId());
            draft.setDate(Dates.toIso(date.getValue()));
            draft.setCategoryEnum(category.getValue() == null
                    ? Choices.ObservationCategory.GENERAL : category.getValue());
            draft.setTitle(Text.clean(title.getText()));
            draft.setContext(Text.clean(contextField.getText()));
            draft.setAuthor(Text.clean(author.getText()));
            draft.setBody(body.getText().trim());
            draft.setConfidential(confidential.isSelected());
            try {
                context.repo().observations().save(draft);
                selectedObservationId = draft.getId();
                context.setCurrentChild(chosen);
                context.notifyDataChanged();
                dialog.close();
            } catch (RuntimeException ex) {
                Ui.error("Could not save this observation", ex);
            }
        };

        HBox buttons = new HBox(8, Ui.spacer(), Ui.button("Cancel", dialog::close),
                Ui.primaryButton(creating ? "Save observation" : "Save changes", save));
        buttons.setPadding(new Insets(0, 18, 16, 18));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scroller = Ui.scroll(new VBox(bodyBox, buttons));
        scroller.setPrefViewportHeight(620);
        Scene scene = new Scene(scroller);
        scene.getStylesheets().addAll(Styles.stylesheet());
        scene.setFill(Color.web("#f4f6f8"));
        dialog.setScene(scene);
        dialog.setOnHidden(event -> refresh());
        dialog.showAndWait();
        Platform.runLater(body::requestFocus);
    }

    private void delete(Observation observation) {
        if (!Ui.confirmDestructive("Delete this observation?",
                "The note and any photos attached to it will be removed. This cannot be undone.",
                "Delete observation")) {
            return;
        }
        try {
            context.repo().deleteObservation(observation.getId());
            selectedObservationId = 0;
            context.notifyDataChanged();
        } catch (RuntimeException ex) {
            Ui.error("Could not delete this observation", ex);
        }
    }

    private void showFormError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    /** Keeps the unused-import check honest: the category list is part of the API. */
    static List<Choices.ObservationCategory> categories() {
        return new ArrayList<>(List.of(Choices.ObservationCategory.values()));
    }
}
