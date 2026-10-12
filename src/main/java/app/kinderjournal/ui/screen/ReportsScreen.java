package app.kinderjournal.ui.screen;

import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.report.ReportOptions;
import app.kinderjournal.report.ReportPreview;
import app.kinderjournal.report.ReportRenderer;
import app.kinderjournal.report.DocumentFormat;
import app.kinderjournal.report.ReportType;
import app.kinderjournal.tools.ReportExporter;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Refreshable;
import app.kinderjournal.ui.Ui;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
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
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Print and reports: choose a document, a class or child, a period and what to
 * include, then preview it before it reaches paper.
 *
 * <p>Documents are generated as print-ready HTML and printed through the system
 * print dialog, which also provides "Microsoft Print to PDF" for saving a report
 * as a PDF. The preview shows the same content, so what is checked is what is
 * printed.
 */
public final class ReportsScreen implements Refreshable {

    private final AppContext context;
    private final MainShell shell;
    private final ScrollPane root = Ui.scroll(new VBox());
    private final ReportRenderer renderer;

    private ReportType type = ReportType.ATTENDANCE_DAY;

    /** Built once and shown or hidden per report type, so the row does not jump. */
    private javafx.scene.control.Button saveAsWord;
    private javafx.scene.control.Button saveAsExcel;
    private long groupId;
    private long childId;
    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();
    private String category = "";
    private String keyword = "";
    private boolean includeProfile = true;
    private boolean includeAttendanceSummary = true;
    private boolean includeAttendanceDetail;
    private boolean includeObservations = true;
    private boolean includeInterviews = true;
    private boolean includePlans;
    private boolean includePhotos = true;
    private boolean includeConfidential;
    private boolean landscape;
    private String notes = "";

    public ReportsScreen(AppContext context, MainShell shell) {
        this.context = context;
        this.shell = shell;
        this.renderer = new ReportRenderer(context.repo());
        this.groupId = context.currentGroupId();
        this.childId = context.currentChildId();
        refresh();
    }

    @Override
    public String title() {
        return Messages.get("reports.title");
    }

    @Override
    public String subtitle() {
        return Messages.get("reports.subtitle");
    }

    @Override
    public Node node() {
        return root;
    }

    // ---------------------------------------------------------------- presets

    /** Preselects the daily attendance sheet for a class and date. */
    public void presetAttendanceDay(long group, LocalDate date) {
        this.type = ReportType.ATTENDANCE_DAY;
        this.groupId = group;
        this.from = date;
        this.to = date;
        refresh();
    }

    /** Preselects an attendance summary for a class and period. */
    public void presetAttendanceSummary(long group, LocalDate fromDate, LocalDate toDate) {
        this.type = ReportType.ATTENDANCE_SUMMARY;
        this.groupId = group;
        this.from = fromDate;
        this.to = toDate;
        refresh();
    }

    /** Preselects a report for one child. */
    public void presetChild(Child child) {
        if (child == null) {
            return;
        }
        this.childId = child.getId();
        if (groupId == 0) {
            this.groupId = child.getGroupId();
        }
        this.type = ReportType.CHILD_HISTORY;
        this.to = LocalDate.now();
        this.from = to.minusMonths(3);
        refresh();
    }

    /** Preselects a child history report. */
    public void presetHistory(Child child, Group group, LocalDate fromDate, LocalDate toDate) {
        if (child != null) {
            this.childId = child.getId();
        }
        this.groupId = group == null ? (child == null ? groupId : child.getGroupId()) : group.getId();
        this.type = ReportType.CHILD_HISTORY;
        this.from = fromDate;
        this.to = toDate;
        refresh();
    }

    /** Preselects the daily activity plan for a class. */
    public void presetDailyPlan(long group, LocalDate day) {
        this.type = ReportType.DAILY_PLAN;
        this.groupId = group;
        this.from = day;
        this.to = day;
        refresh();
    }

    /** Preselects the weekly activity plan for a class. */
    public void presetWeeklyPlan(long group, LocalDate weekStart) {
        this.type = ReportType.WEEKLY_PLAN;
        this.groupId = group;
        this.from = Dates.startOfWeek(weekStart);
        this.to = from.plusDays(6);
        refresh();
    }

    /** Preselects a search-results report. */
    public void presetSearchResults(String searchKeyword, long group, LocalDate fromDate, LocalDate toDate) {
        this.type = ReportType.SEARCH_RESULTS;
        this.keyword = searchKeyword == null ? "" : searchKeyword;
        this.groupId = group;
        this.from = fromDate;
        this.to = toDate;
        refresh();
    }

    // ------------------------------------------------------------------- build

    @Override
    public void refresh() {
        if (groupId == 0) {
            groupId = context.currentGroupId();
        }
        if (type.needsChild()) {
            List<Child> all = context.repo().children().listAll();
            boolean valid = false;
            for (Child child : all) {
                if (child.getId() == childId) {
                    valid = true;
                }
            }
            if (!valid) {
                childId = all.isEmpty() ? 0 : all.get(0).getId();
            }
        }

        VBox page = Ui.page();

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                Ui.screenTitle(Messages.get("reports.build")),
                wrappedMeta(Messages.get("reports.onlyTeacherNotes")),
                Ui.spacer(),
                Ui.primaryButton(Messages.get("reports.preview"), this::preview));
        page.getChildren().add(header);

        HBox columns = new HBox(16);
        VBox left = buildTypeList();
        left.setPrefWidth(320);
        left.setMinWidth(268);
        VBox right = buildOptions();
        // The include-checkboxes and their captions are the point of this screen.
        // The column is pinned to a fixed width rather than left to grow: a form
        // decides its own width from its widest row, and a long checkbox caption
        // would otherwise make the column wider than the window and push it off
        // the right-hand edge, where the captions simply vanish from view.
        right.setMinWidth(650);
        right.setPrefWidth(650);
        right.setMaxWidth(650);
        VBox.setVgrow(right, Priority.NEVER);
        columns.getChildren().addAll(left, right);
        page.getChildren().add(columns);

        page.getChildren().add(buildFootnote());
        root.setContent(page);
    }

    private VBox buildTypeList() {
        VBox list = new VBox(6);
        for (ReportType candidate : ReportType.values()) {
            boolean selected = candidate == type;
            VBox row = new VBox(2);
            row.getStyleClass().add("list-row");
            if (selected) {
                row.getStyleClass().add("list-row-selected");
            }
            Label title = new Label(candidate.label());
            title.getStyleClass().add("row-title");
            Label detail = new Label(candidate.description());
            detail.getStyleClass().add("row-subtitle");
            detail.setWrapText(true);
            // Without this a wrapped label still reports its single-line width as a
            // minimum, which would widen the whole screen past the window.
            detail.setMinWidth(0);
            row.getChildren().addAll(title, detail);
            row.setOnMouseClicked(event -> {
                type = candidate;
                landscape = candidate.defaultLandscape();
                applyFormatAvailability();
                if ((candidate == ReportType.ATTENDANCE_DAY || candidate == ReportType.DAILY_PLAN)
                        && from != null) {
                    to = from;
                }
                if (candidate == ReportType.WEEKLY_PLAN && from != null) {
                    from = Dates.startOfWeek(from);
                    to = from.plusDays(6);
                }
                refresh();
            });
            row.setCursor(javafx.scene.Cursor.HAND);
            list.getChildren().add(row);
        }
        return new VBox(10, Ui.sectionTitle(Messages.get("reports.document")), list);
    }

    private VBox buildOptions() {
        VBox box = new VBox(16);
        box.getChildren().add(Ui.card(Messages.get("reports.whoAndWhen"), buildWhoGrid()));
        box.getChildren().add(Ui.card(Messages.get("reports.whatToInclude"), buildContentOptions()));
        box.getChildren().add(Ui.card(Messages.get("reports.noteToReader"), buildNotesArea()));
        box.getChildren().add(buildPreviewSummary());
        box.getChildren().add(Ui.row(
                Ui.primaryButton(Messages.get("reports.previewAndPrint"), this::preview),
                Ui.button(Messages.get("reports.saveWithoutPrinting"), this::saveOnly),
                wrappedMeta(Messages.get("reports.printHint"))));
        box.getChildren().add(buildSaveAsRow());
        return box;
    }

    /**
     * The "save as" buttons.
     *
     * <p>Which of them apply depends on the report: a register is a table and
     * belongs in a spreadsheet, while an observation report is prose and belongs in
     * a Word document. Both buttons are built and then shown or hidden as the
     * report changes, so the row does not jump about when the choice changes.
     */
    private Node buildSaveAsRow() {
        saveAsWord = Ui.button(Messages.get("format.saveAs", DocumentFormat.WORD.label()),
                () -> saveAs(DocumentFormat.WORD));
        saveAsExcel = Ui.button(Messages.get("format.saveAs", DocumentFormat.EXCEL.label()),
                () -> saveAs(DocumentFormat.EXCEL));
        HBox row = Ui.row(saveAsWord, saveAsExcel,
                wrappedMeta(Messages.get("format.hint")));
        applyFormatAvailability();
        return row;
    }

    /** Shows only the formats that suit the report currently chosen. */
    private void applyFormatAvailability() {
        if (saveAsWord == null || saveAsExcel == null) {
            return;
        }
        boolean word = DocumentFormat.suits(type, DocumentFormat.WORD);
        boolean excel = DocumentFormat.suits(type, DocumentFormat.EXCEL);
        saveAsWord.setVisible(word);
        saveAsWord.setManaged(word);
        saveAsExcel.setVisible(excel);
        saveAsExcel.setManaged(excel);
    }

    private Node buildWhoGrid() {
        GridPane who = Ui.formGrid();

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
        groupChoice.setPrefWidth(220);
        groupChoice.valueProperty().addListener((observable, previous, next) -> {
            groupId = next == null ? 0 : next.getId();
            refresh();
        });

        ComboBox<Child> childChoice = new ComboBox<>();
        childChoice.getItems().addAll(context.repo().children().listAll());
        childChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Child value) {
                return value == null ? Messages.get("reports.chooseChild") : value.fullName()
                        + (value.getGroupName().isEmpty() ? "" : "  \u00b7  " + value.getGroupName());
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
        childChoice.setPrefWidth(250);
        childChoice.setDisable(!type.needsChild());
        childChoice.valueProperty().addListener((observable, previous, next) -> {
            if (next != null && next.getId() != childId) {
                childId = next.getId();
                refresh();
            }
        });

        DatePicker fromPicker = Ui.datePicker(from);
        DatePicker toPicker = Ui.datePicker(to);
        fromPicker.valueProperty().addListener((observable, previous, next) -> from = next);
        toPicker.valueProperty().addListener((observable, previous, next) -> to = next);

        who.add(Ui.field(Messages.get("common.class"), groupChoice), 0, 0);
        who.add(Ui.field(type.needsChild() ? Messages.get("reports.childRequired")
                : Messages.get("reports.childOptional"), childChoice), 1, 0);
        who.add(Ui.field(Messages.get("common.from"), fromPicker), 0, 1);
        who.add(Ui.field(Messages.get("common.to"), toPicker), 1, 1);

        // A FlowPane rather than an HBox: five buttons on one line are wider than
        // the form column, and an HBox reports that width as its minimum, which
        // pushed the whole page wider than the window. Wrapping keeps the row
        // inside the column.
        javafx.scene.layout.FlowPane quick = new javafx.scene.layout.FlowPane(8, 6,
                Ui.button(Messages.get("common.today"), () -> setRange(LocalDate.now(), LocalDate.now())),
                Ui.button(Messages.get("common.thisWeek"), () -> {
                    LocalDate start = Dates.startOfWeek(LocalDate.now());
                    setRange(start, start.plusDays(6));
                }),
                Ui.button(Messages.get("common.thisMonth"), () -> {
                    LocalDate first = LocalDate.now().withDayOfMonth(1);
                    setRange(first, first.withDayOfMonth(first.lengthOfMonth()));
                }),
                Ui.button(Messages.get("common.last3Months"),
                        () -> setRange(LocalDate.now().minusMonths(3), LocalDate.now())),
                Ui.button(Messages.get("common.schoolYear"), () -> {
                    LocalDate today = LocalDate.now();
                    int startYear = today.getMonthValue() >= 8 ? today.getYear() : today.getYear() - 1;
                    setRange(LocalDate.of(startYear, 8, 1), today);
                }));
        who.add(Ui.fieldLabel(Messages.get("reports.quickRanges")), 0, 2, 2, 1);
        who.add(quick, 0, 3, 2, 1);
        return who;
    }

    private void setRange(LocalDate fromDate, LocalDate toDate) {
        from = fromDate;
        to = toDate;
        refresh();
    }

    private Node buildContentOptions() {
        VBox content = new VBox(8);
        content.getChildren().addAll(
                includeCheck(Messages.get("reports.include.profile"), includeProfile,
                        value -> includeProfile = value),
                includeCheck(Messages.get("reports.include.attendanceSummary"), includeAttendanceSummary,
                        value -> includeAttendanceSummary = value),
                includeCheck(Messages.get("reports.include.attendanceDetail"), includeAttendanceDetail,
                        value -> includeAttendanceDetail = value),
                includeCheck(Messages.get("reports.include.observations"), includeObservations,
                        value -> includeObservations = value),
                includeCheck(Messages.get("reports.include.interviews"), includeInterviews,
                        value -> includeInterviews = value),
                includeCheck(Messages.get("reports.include.plans"), includePlans,
                        value -> includePlans = value),
                includeCheck(Messages.get("reports.include.photos"), includePhotos,
                        value -> includePhotos = value),
                includeCheck(Messages.get("reports.include.confidential"), includeConfidential,
                        value -> includeConfidential = value));

        ComboBox<String> categoryChoice = new ComboBox<>();
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("", Messages.get("common.allObsCategories"));
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
        categoryChoice.setPrefWidth(280);
        categoryChoice.valueProperty().addListener((observable, previous, next) -> {
            category = next == null ? "" : next;
            refresh();
        });
        content.getChildren().add(Ui.field(Messages.get("reports.restrictCategory"), categoryChoice));

        if (type == ReportType.SEARCH_RESULTS) {
            TextField keywordField = Ui.textField(keyword, Messages.get("reports.searchKeywordHint"),
                    24);
            keywordField.textProperty().addListener((observable, previous, next) -> keyword = next);
            content.getChildren().add(Ui.field(Messages.get("reports.searchKeyword"), keywordField));
        }

        CheckBox landscapeBox = new CheckBox(Messages.get("reports.landscape"));
        landscapeBox.setSelected(landscape);
        landscapeBox.selectedProperty().addListener((observable, previous, next) -> landscape = next);
        content.getChildren().add(landscapeBox);
        return content;
    }

    private TextArea buildNotesArea() {
        TextArea notesArea = Ui.textArea(notes, 3);
        notesArea.setPromptText(Messages.get("reports.noteHint"));
        notesArea.textProperty().addListener((observable, previous, next) -> notes = next);
        return notesArea;
    }

    private CheckBox includeCheck(String label, boolean value,
                                  java.util.function.Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(label);
        box.setSelected(value);
        // Captions are allowed to wrap. A CheckBox measures itself from the full
        // width of its text, so a long caption would otherwise demand more width
        // than the window has and push the whole column off the right-hand edge -
        // which makes the labels look as though they were never drawn.
        box.setWrapText(true);
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        box.selectedProperty().addListener((observable, previous, next) -> setter.accept(next));
        return box;
    }

    private Node buildPreviewSummary() {
        VBox list = new VBox(4);
        try {
            Map<String, Integer> counts = renderer.preview(currentOptions());
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                list.getChildren().add(Ui.keyValue(entry.getKey(), String.valueOf(entry.getValue())));
            }
        } catch (RuntimeException ex) {
            list.getChildren().add(wrappedMeta(Messages.get("reports.summaryNeedsSelection")));
        }
        String warning = validationMessage();
        if (warning != null) {
            HBox banner = new HBox(8);
            banner.getStyleClass().add("warning-banner");
            Label label = new Label(warning);
            label.setWrapText(true);
            banner.getChildren().add(label);
            list.getChildren().add(banner);
        }
        return Ui.cardWithHeader(Messages.get("reports.willContain"),
                Ui.pill(type.label(), "pill-planned"), list);
    }

    private Node buildFootnote() {
        VBox box = new VBox(6);
        box.getChildren().add(wrappedMeta(Messages.get("reports.footnote")));
        box.getChildren().add(wrappedMeta(
                Messages.get("status.dataFolder", AppPaths.dataDir().toAbsolutePath())));
        return box;
    }

    /**
     * A caption that wraps instead of dictating the width of the whole screen.
     *
     * <p>A Label measures its minimum width from its text on a single line even
     * when wrapping is enabled. A long sentence in a footnote therefore made this
     * screen demand more width than the window had, and the extra width pushed the
     * right-hand column - the include-checkboxes and their captions - off the
     * visible area, where they looked as though they had never been drawn.
     */
    private static Label wrappedMeta(String text) {
        Label label = Ui.meta(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(560);
        return label;
    }

    // ------------------------------------------------------------------ options

    private ReportOptions currentOptions() {
        return ReportOptions.builder(type)
                .group(groupId)
                .child(childId)
                .range(from, to)
                .category(category)
                .keyword(keyword)
                .teacher(context.teacherName())
                .notes(notes)
                .profile(includeProfile)
                .attendanceSummary(includeAttendanceSummary)
                .attendanceDetail(includeAttendanceDetail)
                .observations(includeObservations)
                .interviews(includeInterviews)
                .plans(includePlans)
                .photos(includePhotos)
                .confidential(includeConfidential)
                .landscape(landscape)
                .build();
    }

    /** A plain-language reason the report cannot be built yet, or null. */
    private String validationMessage() {
        if (type.needsChild() && childId == 0) {
            return Messages.get("reports.error.noChild");
        }
        if (type.needsGroup() && groupId == 0) {
            return Messages.get("reports.error.noClass");
        }
        if (type == ReportType.SEARCH_RESULTS && Text.isBlank(keyword)) {
            return Messages.get("reports.error.noKeyword");
        }
        if (type != ReportType.SEARCH_RESULTS && from == null) {
            return Messages.get("reports.error.noFrom");
        }
        if (from != null && to != null && from.isAfter(to)) {
            return Messages.get("reports.error.rangeOrder");
        }
        return null;
    }

    // ---------------------------------------------------------------- preview

    private void preview() {
        ReportRenderer.Rendered rendered = generate();
        if (rendered == null) {
            return;
        }
        ReportPreview.show(rendered, shell.window(), type.label());
    }

    /** Saves the HTML without opening the print dialog. */
    private void saveOnly() {
        ReportRenderer.Rendered rendered = generate();
        if (rendered == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("reports.saveTitle"));
        chooser.setInitialFileName(ReportRenderer.suggestedFileName(currentOptions(), type.label()));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Messages.get("reports.saveFilter"), "*.html"));
        java.io.File chosen = chooser.showSaveDialog(shell.window());
        if (chosen == null) {
            return;
        }
        try {
            Files.writeString(chosen.toPath(), rendered.html());
            if (Ui.confirm(Messages.get("reports.saved"),
                    Messages.get("reports.savedBody") + "\n" + chosen.getAbsolutePath()
                            + "\n\n" + Messages.get("reports.openIt"),
                    Messages.get("reports.openItAction"))) {
                ReportPreview.openExternally(chosen.toPath());
            }
        } catch (IOException ex) {
            Ui.error(Messages.get("reports.couldNotSave"), ex);
        }
    }

    /**
     * Saves the report as a Word or Excel file.
     *
     * <p>The report is rendered exactly as it would be for printing, then parsed
     * back out of that HTML, so what is saved is the same document the teacher just
     * looked at rather than a second implementation of it.
     *
     * <p>The destination is chosen with KinderJournal's own dialog rather than the
     * Windows one. The Windows dialog refuses to save for some computers, reporting
     * a permission problem for every folder even when the program can write to them
     * perfectly well; using our own avoids that entirely.
     */
    private void saveAs(DocumentFormat format) {
        ReportRenderer.Rendered rendered = generate();
        if (rendered == null) {
            return;
        }
        String base = ReportRenderer.suggestedFileName(currentOptions(), type.label());
        int dot = base.lastIndexOf('.');
        String suggested = (dot > 0 ? base.substring(0, dot) : base) + "." + format.extension();

        java.nio.file.Path target =
                app.kinderjournal.ui.SaveLocationDialog.choose(shell.window(), format,
                        suggested, context);
        if (target == null) {
            return;
        }
        try {
            ReportExporter.writeOffice(rendered.html(), format, target);
            long size = java.nio.file.Files.size(target);
            if (Ui.confirm(Messages.get("format.savedTitle"),
                    Messages.get("format.saved") + "\n" + target.toAbsolutePath()
                            + "\n(" + readableSize(size) + ")\n\n"
                            + Messages.get("format.savedWhere"),
                    Messages.get("format.openFolder"))) {
                openFolder(target.getParent());
            }
        } catch (IllegalArgumentException ex) {
            // The report had nothing of the kind this format needs, which is worth
            // saying plainly rather than showing as a failure to save.
            Ui.warn(Messages.get("format.saveTitle"), ex.getMessage());
        } catch (IOException | RuntimeException ex) {
            Ui.error(Messages.get("format.couldNotSave"), ex);
        }
    }

    /** A size a teacher can read, for the confirmation. */
    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Opens a folder in the computer's file browser. */
    private static void openFolder(java.nio.file.Path folder) {
        if (folder == null) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | RuntimeException ex) {
            // UnsupportedOperationException is a RuntimeException, so it is covered.
            // Not worth an error dialog: the path was already shown to the teacher.
            Ui.info(Messages.get("format.savedTitle"), folder.toAbsolutePath().toString());
        }
    }

    /** Validates, generates, and reports any problem; null when it could not build. */
    private ReportRenderer.Rendered generate() {        String problem = validationMessage();
        if (problem != null) {
            Ui.warn(Messages.get("reports.notReady"), problem);
            return null;
        }
        try {
            ReportRenderer.Rendered rendered = renderer.render(currentOptions());
            if (rendered.isEmpty()) {
                Ui.warn(Messages.get("reports.nothingToPrint"),
                        Messages.get("reports.nothingToPrintBody"));
                return null;
            }
            return rendered;
        } catch (RuntimeException ex) {
            Ui.error(Messages.get("reports.couldNotGenerate"), ex);
            return null;
        }
    }
}
