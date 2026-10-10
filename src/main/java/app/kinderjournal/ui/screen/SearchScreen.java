package app.kinderjournal.ui.screen;

import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.SearchHit;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.RecordList;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced search across every kind of record stored on this computer.
 *
 * <p>Criteria can be combined - a keyword, a class, one child, a date range and a
 * record type - and results carry the date, type, child and a snippet with the
 * match highlighted, so the right record can be recognised without opening each
 * one in turn.
 */
public final class SearchScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());
    private final TextField keywordField = Ui.textField("", Messages.get("search.field"), 30);

    private String keyword = "";
    private long groupId;
    private long childId;
    private LocalDate from;
    private LocalDate to;
    private String category = "";
    private String interviewType = "";
    private String planStatus = "";
    private String planArea = "";
    private boolean includeChildren = true;
    private boolean includeObservations = true;
    private boolean includeInterviews = true;
    private boolean includePlans = true;
    private int limit = 300;

    private List<SearchHit> results = new ArrayList<>();
    private boolean searched;
    private String elapsed = "";

    public SearchScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.groupId = context.currentGroupId();
        this.to = LocalDate.now();
        this.from = to.minusMonths(12);
        refresh();
    }

    @Override
    public String title() {
        return Messages.get("search.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("search.subtitle");
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        VBox page = Ui.page();
        page.getChildren().add(buildForm());
        page.getChildren().add(buildResults());
        root.setContent(page);
    }

    /** Runs a search from another screen (used by dashboard shortcuts). */
    public void searchFor(String text) {
        keyword = text == null ? "" : text.trim();
        keywordField.setText(keyword);
        runSearch();
    }

    // -------------------------------------------------------------------- form

    private Node buildForm() {
        VBox card = new VBox(12);

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        keywordField.setText(keyword);
        keywordField.setOnAction(event -> runSearch());
        top.getChildren().addAll(keywordField,
                Ui.primaryButton(Messages.get("button.search"), this::runSearch),
                Ui.button(Messages.get("common.clear"), this::clearAll));
        card.getChildren().add(top);

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Group> groupChoice = new ComboBox<>();
        groupChoice.getItems().add(null);
        groupChoice.getItems().addAll(context.repo().groups().listAll());
        groupChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group value) {
                return value == null ? Messages.get("common.allClasses") : value.getName();
            }

            @Override
            public Group fromString(String text) {
                return null;
            }
        });
        for (Group group : context.repo().groups().listAll()) {
            if (group.getId() == groupId) {
                groupChoice.setValue(group);
            }
        }
        groupChoice.setPrefWidth(190);
        groupChoice.valueProperty().addListener((observable, previous, next) -> {
            groupId = next == null ? 0 : next.getId();
            refresh();
        });

        ComboBox<Child> childChoice = new ComboBox<>();
        childChoice.getItems().add(null);
        childChoice.getItems().addAll(context.repo().children().listAll());
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child value) {
                return value == null ? Messages.get("common.allChildren") : value.fullName();
            }

            @Override
            public Child fromString(String text) {
                return null;
            }
        });
        for (Child child : context.repo().children().listAll()) {
            if (child.getId() == childId) {
                childChoice.setValue(child);
            }
        }
        childChoice.setPrefWidth(180);
        childChoice.valueProperty().addListener((observable, previous, next) -> {
            childId = next == null ? 0 : next.getId();
            refresh();
        });

        DatePicker fromPicker = Ui.datePicker(from);
        DatePicker toPicker = Ui.datePicker(to);
        fromPicker.valueProperty().addListener((observable, previous, next) -> from = next);
        toPicker.valueProperty().addListener((observable, previous, next) -> to = next);

        filters.getChildren().addAll(Ui.fieldLabel(Messages.get("common.class")), groupChoice,
                Ui.fieldLabel(Messages.get("common.child")), childChoice,
                Ui.fieldLabel(Messages.get("common.from")), fromPicker,
                Ui.fieldLabel(Messages.get("common.to")), toPicker);
        card.getChildren().add(filters);

        HBox types = new HBox(14);
        types.setAlignment(Pos.CENTER_LEFT);
        types.getChildren().add(Ui.fieldLabel(Messages.get("search.recordTypes")));
        types.getChildren().addAll(
                typeCheck(Messages.get("search.type.children"), includeChildren,
                        value -> includeChildren = value),
                typeCheck(Messages.get("search.type.observations"), includeObservations,
                        value -> includeObservations = value),
                typeCheck(Messages.get("search.type.interviews"), includeInterviews,
                        value -> includeInterviews = value),
                typeCheck(Messages.get("search.type.plans"), includePlans,
                        value -> includePlans = value));
        card.getChildren().add(types);

        HBox narrow = new HBox(10);
        narrow.setAlignment(Pos.CENTER_LEFT);

        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("", Messages.get("common.anyCategory"));
        categories.putAll(Choices.observationLabelMap());
        narrow.getChildren().addAll(
                Ui.fieldLabel(Messages.get("search.obsCategory")),
                labelledCombo(categories, category, value -> {
                    category = value;
                    refresh();
                }, 210),
                Ui.fieldLabel(Messages.get("search.interviewType")),
                labelledCombo(interviewTypes(), interviewType, value -> {
                    interviewType = value;
                    refresh();
                }, 200),
                Ui.fieldLabel(Messages.get("search.planStatus")),
                labelledCombo(planStatuses(), planStatus, value -> {
                    planStatus = value;
                    refresh();
                }, 170));
        card.getChildren().add(narrow);

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.getChildren().addAll(
                Ui.fieldLabel(Messages.get("search.planSubject")),
                labelledCombo(planAreas(), planArea, value -> {
                    planArea = value;
                    refresh();
                }, 200),
                Ui.spacer(),
                Ui.button(Messages.get("button.searchAgain"), this::runSearch),
                Ui.button(Messages.get("button.sendToReport"), this::sendToReport));
        card.getChildren().add(bottom);

        return Ui.card(Messages.get("search.criteria"), card);
    }

    private CheckBox typeCheck(String label, boolean selected, java.util.function.Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(label);
        box.setSelected(selected);
        box.selectedProperty().addListener((observable, previous, next) -> setter.accept(next));
        return box;
    }

    private <T> ComboBox<String> labelledCombo(Map<String, String> values, String current,
                                              java.util.function.Consumer<String> setter, double width) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(values.keySet());
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : values.getOrDefault(id, id);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        combo.setValue(current == null ? "" : current);
        combo.setPrefWidth(width);
        combo.valueProperty().addListener((observable, previous, next) ->
                setter.accept(next == null ? "" : next));
        return combo;
    }

    private Map<String, String> interviewTypes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("", Messages.get("common.anyType"));
        for (Choices.InterviewType type : Choices.InterviewType.values()) {
            map.put(type.id(), type.label());
        }
        return map;
    }

    private Map<String, String> planStatuses() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("", Messages.get("common.anyStatus"));
        for (Choices.PlanStatus status : Choices.PlanStatus.values()) {
            map.put(status.id(), status.label());
        }
        return map;
    }

    private Map<String, String> planAreas() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("", Messages.get("common.anySubject"));
        for (Choices.PlanArea area : Choices.PlanArea.values()) {
            map.put(area.id(), area.label());
        }
        return map;
    }

    private void clearAll() {
        keyword = "";
        keywordField.setText("");
        groupId = 0;
        childId = 0;
        from = null;
        to = null;
        category = "";
        interviewType = "";
        planStatus = "";
        planArea = "";
        includeChildren = true;
        includeObservations = true;
        includeInterviews = true;
        includePlans = true;
        results = new ArrayList<>();
        searched = false;
        refresh();
    }

    // ----------------------------------------------------------------- results

    private void runSearch() {
        keyword = keywordField.getText() == null ? "" : keywordField.getText().trim();
        long started = System.nanoTime();
        Repository.SearchQuery query = new Repository.SearchQuery(keyword, childId, groupId,
                from, to, category, interviewType, planStatus, planArea,
                includeChildren, includeObservations, includeInterviews, includePlans, limit);
        if (query.isEmpty()) {
            Ui.warn(Messages.get("search.nothingToSearch"),
                    Messages.get("search.nothingToSearchBody"));
            return;
        }
        try {
            results = context.repo().search(query);
            searched = true;
            elapsed = ((System.nanoTime() - started) / 1_000_000) + " ms";
        } catch (RuntimeException ex) {
            Ui.error(Messages.get("search.failed"), ex);
            results = new ArrayList<>();
            searched = true;
            elapsed = "";
        }
        refresh();
    }

    private Node buildResults() {
        VBox box = new VBox(10);

        if (!searched) {
            box.getChildren().add(Ui.emptyState(Messages.get("search.empty.title"),
                    Messages.get("search.empty.body"), null));
            return Ui.card(Messages.get("search.results"), box);
        }

        HBox summary = new HBox(10);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.getChildren().addAll(
                Ui.screenTitle(results.size() == 1
                        ? Messages.get("search.match")
                        : Messages.get("search.matches", results.size())),
                Text.isBlank(elapsed) ? new Label()
                        : Ui.meta(Messages.get("search.inTime", elapsed)));
        if (results.size() >= limit) {
            summary.getChildren().add(
                    Ui.pill(Messages.get("search.showingFirst", limit), "pill-count"));
        }
        summary.getChildren().addAll(Ui.spacer(),
                Ui.button(Messages.get("button.exportResults"), this::sendToReport));
        box.getChildren().add(summary);

        if (results.isEmpty()) {
            box.getChildren().add(Ui.emptyState(Messages.get("search.noMatches.title"),
                    Messages.get("search.noMatches.body"), null));
            return Ui.card(Messages.get("search.results"), box);
        }

        // Group the hits by month so a long list stays readable.
        String currentMonth = "\u0000";
        for (SearchHit hit : results) {
            String month = Text.isBlank(hit.date()) ? "" : hit.date().substring(0, 7);
            if (!month.equals(currentMonth)) {
                currentMonth = month;
                Label header = new Label(Text.isBlank(month) ? Messages.get("search.profiles")
                        : monthHeader(month));
                header.getStyleClass().add("section-title");
                header.setPadding(new javafx.geometry.Insets(8, 0, 0, 2));
                box.getChildren().add(header);
            }
            box.getChildren().add(RecordList.searchResult(hit, keyword, this::openHit));
        }
        return Ui.card(Messages.get("search.results"), box);
    }

    private String monthHeader(String month) {
        java.time.YearMonth yearMonth = java.time.YearMonth.parse(month);
        return Dates.monthName(yearMonth.getYear(), yearMonth.getMonthValue());
    }

    /** Opens the record behind a search hit. */
    private void openHit(SearchHit hit) {
        Child child = hit.childId() > 0 ? context.repo().children().find(hit.childId()) : null;
        switch (hit.kind()) {
            case OBSERVATION -> {
                if (child != null) {
                    context.setCurrentChild(child);
                }
                shell.show("observations");
            }
            case INTERVIEW -> {
                if (child != null) {
                    context.setCurrentChild(child);
                }
                shell.show("interviews");
            }
            case PLAN -> shell.showAnd("plans", target -> {
                if (target instanceof PlansScreen plans && !Text.isBlank(hit.date())) {
                    plans.openNewForm(Dates.fromIso(hit.date()));
                }
            });
            case ATTENDANCE -> {
                if (child != null) {
                    context.setCurrentChild(child);
                }
                shell.showAnd("history", target -> {
                    if (target instanceof HistoryScreen history && child != null) {
                        history.selectChild(child);
                    }
                });
            }
            default -> {
                if (child != null) {
                    context.setCurrentChild(child);
                    shell.show("children");
                }
            }
        }
    }

    private void sendToReport() {
        if (results.isEmpty()) {
            Ui.warn(Messages.get("search.nothingToSend"),
                    Messages.get("search.nothingToSendBody"));
            return;
        }
        long group = groupId;
        LocalDate start = from;
        LocalDate end = to;
        String text = keyword;
        shell.showAnd("reports", target -> {
            if (target instanceof ReportsScreen reports) {
                reports.presetSearchResults(text, group, start, end);
            }
        });
    }

    /** The criteria currently in force, for the report header. */
    public String criteriaSummary() {
        List<String> parts = new ArrayList<>();
        if (!Text.isBlank(keyword)) {
            parts.add(Messages.get("search.criteria.keyword", keyword));
        }
        if (groupId > 0) {
            Group group = context.repo().groups().find(groupId);
            if (group != null) {
                parts.add(Messages.get("search.criteria.class", group.getName()));
            }
        }
        if (childId > 0) {
            Child child = context.repo().children().find(childId);
            if (child != null) {
                parts.add(Messages.get("search.criteria.child", child.fullName()));
            }
        }
        if (from != null || to != null) {
            String start = from == null
                    ? Messages.get("search.criteria.start") : Dates.display(Dates.toIso(from));
            String end = to == null
                    ? Messages.get("common.today") : Dates.display(Dates.toIso(to));
            parts.add(Messages.get("search.criteria.dates", start, end));
        }
        if (!Text.isBlank(category)) {
            parts.add(Messages.get("search.criteria.category", Choices.observationLabel(category)));
        }
        return parts.isEmpty()
                ? Messages.get("search.criteriaSummary") : String.join(", ", parts);
    }

    /** The current keyword, for callers that want to pre-fill a report. */
    public String keyword() {
        return keyword;
    }

    /** The current result list, for the report builder. */
    public List<SearchHit> results() {
        return results;
    }

    /** True when a search has been run. */
    public boolean hasResults() {
        return searched && !results.isEmpty();
    }

    /** The kinds included, for the report header. */
    public List<HistoryEntry.Kind> includedKinds() {
        List<HistoryEntry.Kind> kinds = new ArrayList<>();
        if (includeObservations) {
            kinds.add(HistoryEntry.Kind.OBSERVATION);
        }
        if (includeInterviews) {
            kinds.add(HistoryEntry.Kind.INTERVIEW);
        }
        if (includePlans) {
            kinds.add(HistoryEntry.Kind.PLAN);
        }
        return kinds;
    }
}
