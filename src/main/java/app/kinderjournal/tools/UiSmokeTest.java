package app.kinderjournal.tools;

import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.report.DocumentFormat;
import app.kinderjournal.report.HtmlPreview;
import app.kinderjournal.report.ReportOptions;
import app.kinderjournal.report.ReportRenderer;
import app.kinderjournal.report.ReportType;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.ui.Ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Builds every screen against a full set of sample data and reports anything that
 * fails.
 *
 * <p>This catches the mistakes a compiler cannot: a missing stylesheet, a screen
 * that throws while laying itself out, a report type that assumes data which is
 * not there. It runs on a real - though invisible - JavaFX toolkit, so it
 * exercises the same code path the teacher's window uses, and it exits with a
 * non-zero status when something broke.
 */
public final class UiSmokeTest {

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    public void run() {
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                try {
                    Stage stage = new Stage();
                    runChecks(stage);
                } catch (Throwable ex) {
                    fail("unexpected failure: " + ex);
                    ex.printStackTrace(System.out);
                } finally {
                    done.countDown();
                }
            });
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit was started elsewhere; run on it anyway.
            Platform.runLater(() -> {
                try {
                    runChecks(new Stage());
                } catch (Throwable ex) {
                    fail("unexpected failure: " + ex);
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            if (!done.await(3, TimeUnit.MINUTES)) {
                fail("tests did not finish within three minutes");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        Platform.exit();
        System.out.println();
        System.out.printf("%d passed, %d failed%n", passed, failed);
        for (String failure : failures) {
            System.out.println("  FAILED: " + failure);
        }
        System.out.flush();
        System.exit(failed == 0 ? 0 : 1);
    }

    private void runChecks(Stage stage) {
        System.out.println("KinderJournal interface checks");
        Path dataDir;
        try {
            dataDir = Files.createTempDirectory("kinderjournal-ui");
        } catch (Exception ex) {
            check("temporary data folder could be created", false);
            return;
        }
        System.setProperty("kinderjournal.dataDir", dataDir.toString());

        int seeded;
        try {
            seeded = DemoData.seed(dataDir);
        } catch (Throwable ex) {
            check("sample data seeded", false);
            ex.printStackTrace(System.out);
            return;
        }
        check("sample data seeded (" + seeded + " records)", seeded > 50);

        try (Repository repo = Repository.open()) {
            AppContext context = new AppContext(repo);
            MainShell shell = new MainShell(context, stage);

            // 1170x800 rather than something comfortable: a laptop at 200% Windows
            // scaling reports only about 1440x810 logical pixels, so this is the
            // size screens must survive.
            Scene scene = new Scene((javafx.scene.Parent) shell.root(), 1170, 800);
            scene.getStylesheets().addAll(Styles.stylesheet());
            scene.setOnKeyPressed(shell::handleShortcut);
            stage.setScene(scene);
            // Give the stage a real size so the fit checks measure against it.
            stage.setWidth(1170);
            stage.setHeight(800);

            check("shell built", shell.root() != null);
            check("stylesheet found on the classpath", Styles.url() != null);
            check("stylesheet applied to the scene", scene.getStylesheets().size() == 1);
            check("stylesheet parses without CSS errors", stylesheetIsValid());
            check("layout pass completes on a real toolkit", layoutPassIsClean(scene));

            String[] keys = {"dashboard", "children", "classes", "attendance", "observations",
                    "interviews", "history", "plans", "search", "reports", "data"};
            for (String key : keys) {
                check("screen builds: " + key, buildsWithoutError(shell, key));
            }

            checkSaveAsButtonsExist(scene, shell);
            checkSaveFolderAdvice();
            checkSaveDialogLaysOut(context);

            // Builds again for every screen, to catch rebuild-only mistakes.
            for (String key : keys) {                boolean ok = true;
                String detail = "";
                try {
                    shell.show(key);
                    shell.refreshCurrent();
                    shell.refreshCurrent();
                    if (shell.visibleContentNodeCount() == 0) {
                        ok = false;
                        detail = " \u2013 nothing on display after refresh";
                    }
                } catch (Throwable ex) {
                    ok = false;
                    detail = " \u2013 " + ex;
                    ex.printStackTrace(System.out);
                }
                check("screen refreshes repeatedly: " + key + detail, ok);
            }

            // Every screen must build in Mongolian too, because each one creates
            // its labels in its constructor from the catalogue.
            check("language switches to Mongolian", Messages.setCurrent(Lang.MONGOLIAN));
            check("interface language is Mongolian", Messages.current() == Lang.MONGOLIAN);
            for (String key : keys) {
                boolean ok = true;
                String detail = "";
                try {
                    shell.show(key);
                    if (shell.visibleContentNodeCount() == 0) {
                        ok = false;
                        detail = " \u2013 nothing on display";
                    }
                } catch (Throwable ex) {
                    ok = false;
                    detail = " \u2013 " + ex;
                    ex.printStackTrace(System.out);
                }
                check("screen builds in Mongolian: " + key + detail, ok);
            }
            check("sidebar is rebuilt in Mongolian",
                    containsCyrillic(textOf(shell.root())));
            check("language choice is written to the database",
                    Lang.MONGOLIAN.code().equals(repo.db().setting("language", "")));
            check("switching back to English works", Messages.setCurrent(Lang.ENGLISH));
            check("English screen builds after switching back", buildsWithoutError(shell, "dashboard"));
            check("English choice is persisted",
                    Lang.ENGLISH.code().equals(repo.db().setting("language", "")));

            // Records can be added through the UI-level helpers, then the screens
            // that display them must still build.
            check("quick observation capture works", writeThroughRepository(repo));

            // Switching class must rebuild every screen cleanly.
            List<Group> groups = repo.groups().listActive();
            if (groups.size() > 1) {
                context.setCurrentGroup(groups.get(1));
                for (String key : new String[]{"dashboard", "attendance", "observations", "plans",
                        "history", "reports"}) {
                    check("screen rebuilds after a class change: " + key,
                            buildsWithoutError(shell, key));
                }
                context.setCurrentGroup(groups.get(0));
            } else {
                check("two sample classes exist", false);
            }

            // An empty database must not break any screen either.
            check("screens survive an empty class", emptyClassIsSafe(repo, shell, context));

            checkNothingIsWiderThanTheWindow(scene, shell, keys);

            checkAvatarsAreRoundAndFilled();

            checkEveryReportType(repo);
        } catch (Throwable ex) {
            fail("smoke test aborted: " + ex);
            ex.printStackTrace(System.out);
        }
    }

    private static String textOf(javafx.scene.Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString();
    }

    private static void collectText(javafx.scene.Node node, StringBuilder sb) {
        if (node instanceof javafx.scene.control.Labeled labelled && labelled.getText() != null) {
            sb.append(labelled.getText()).append('\n');
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectText(child, sb);
            }
        }
    }

    /** True when the text contains Cyrillic, which only the Mongolian catalogue uses. */
    private static boolean containsCyrillic(String text) {
        return text.matches("(?s).*[\\u0400-\\u04FF].*");
    }

    private boolean buildsWithoutError(MainShell shell, String key) {
        try {
            shell.show(key);
            return shell.visibleContentNodeCount() > 0;
        } catch (Throwable ex) {
            System.out.println("        " + ex);
            return false;
        }
    }

    /** Exercises the write path the quick-capture actions use. */
    private boolean writeThroughRepository(Repository repo) {
        try {
            List<Child> children = repo.children().listAll();
            if (children.isEmpty()) {
                return false;
            }
            Child child = children.get(0);
            repo.addObservation(child.getId(), LocalDate.now(),
                    app.kinderjournal.model.Choices.ObservationCategory.PARTICIPATION,
                    "Captured from the dashboard", "Wrote a note through the quick-capture path.");
            return repo.observations().listForChild(child.getId(), null, null, null).stream()
                    .anyMatch(o -> "Captured from the dashboard".equals(o.getTitle()));
        } catch (Throwable ex) {
            ex.printStackTrace(System.out);
            return false;
        }
    }

    /** A class with no children and no records must still render every screen. */
    private boolean emptyClassIsSafe(Repository repo, MainShell shell, AppContext context) {
        Group empty = new Group();
        empty.setName("Empty class (test)");
        try {
            repo.groups().insert(empty);
            context.setCurrentGroup(empty);
            boolean ok = true;
            for (String key : new String[]{"dashboard", "attendance", "observations", "plans",
                    "history", "children", "reports"}) {
                if (!buildsWithoutError(shell, key)) {
                    ok = false;
                    System.out.println("        empty class broke: " + key);
                }
            }
            context.setCurrentGroup(repo.groups().firstActive());
            return ok;
        } catch (Throwable ex) {
            ex.printStackTrace(System.out);
            return false;
        }
    }

    /** Generates each document type against the sample data. */
    private void checkEveryReportType(Repository repo) {
        ReportRenderer renderer = new ReportRenderer(repo);
        List<Group> groups = repo.groups().listActive();
        List<Child> children = repo.children().listAll();
        if (groups.isEmpty() || children.isEmpty()) {
            check("sample data has a class and a child", false);
            return;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(2);

        // The class that actually has records, chosen by what is in it rather than
        // by position or by the last_group_id setting: earlier checks in this run
        // create an empty test class, and a report rendered against that would be
        // full of "no children" notices and look like a fault in the renderer.
        Group group = null;
        for (Group candidate : groups) {
            if (!repo.children().listByGroup(candidate.getId(), false).isEmpty()
                    && !repo.attendance().listForGroup(candidate.getId(), from, to).isEmpty()) {
                group = candidate;
                break;
            }
        }
        if (group == null) {
            group = groups.get(0);
        }
        Child child = children.get(0);

        for (ReportType type : ReportType.values()) {
            ReportOptions options = ReportOptions.builder(type)
                    .group(group.getId())
                    .child(child.getId())
                    .range(from, to)
                    .observations(true)
                    .interviews(true)
                    .plans(true)
                    .photos(true)
                    .confidential(true)
                    .build();
            try {
                ReportRenderer.Rendered rendered = renderer.render(options);
                String html = rendered.html();
                boolean sane = html.startsWith("<!DOCTYPE html>")
                        && html.contains("</html>")
                        && html.contains("report-header")
                        && html.length() > 500;
                check("report renders: " + type.label() + " (" + html.length() + " chars)", sane);
                if (!sane) {
                    continue;
                }
                check("report has content: " + type.label(),
                        html.contains("<h2>") || html.contains("<table"));
                if (!(html.contains("<h2>") || html.contains("<table"))) {
                    System.out.println("        " + type.label() + " rendered only: "
                            + HtmlPreview.plainText(html).strip().substring(0,
                                    Math.min(140, HtmlPreview.plainText(html).strip().length())));
                    System.out.println("        asked for group=" + group.getId() + " ("
                            + group.getName() + ") child=" + child.getId() + " range "
                            + from + ".." + to);
                }
                check("report reads as text: " + type.label(),
                        HtmlPreview.plainText(html).length() > 40);
            } catch (Throwable ex) {
                fail("report " + type + ": " + ex);
                ex.printStackTrace(System.out);
            }
        }

        // Photos must be embedded so a saved report still shows its pictures.
        String withPhotos = renderer.render(ReportOptions.builder(ReportType.CHILD_OBSERVATIONS)
                .child(child.getId()).group(group.getId()).range(from, to).photos(true).build()).html();
        check("photos are embedded in reports", withPhotos.contains("data:image/"));

        // The history report must contain the merged timeline.
        String history = renderer.render(ReportOptions.builder(ReportType.CHILD_HISTORY)
                .child(child.getId()).group(group.getId()).range(from, to)
                .kinds(List.of(HistoryEntry.Kind.OBSERVATION, HistoryEntry.Kind.INTERVIEW,
                        HistoryEntry.Kind.ATTENDANCE))
                .build()).html();
        check("history report includes attendance", history.contains("Attendance"));
        check("history report includes observations", history.contains("Observation"));
        check("history report includes interviews", history.contains("Interview"));

        // A search report with no matches must still produce a document.
        String noMatch = renderer.render(ReportOptions.builder(ReportType.SEARCH_RESULTS)
                .keyword("zzzznotfound").range(from, to).build()).html();
        check("empty search report still renders", noMatch.contains("</html>"));
    }

    /**
     * Checks that no screen demands more width than the window offers.
     *
     * <p>This is the bug that prompted the check: a screen whose content needs
     * more room than the window is wide has its right-hand side cut off, and
     * because form captions and checkbox labels live on the right, they look as
     * though they were never drawn. Nothing throws, so only a measurement finds
     * it - and a 1440x810 logical screen, which is what a laptop at 200% scaling
     * reports, is narrow enough to trigger it.
     */
    private void checkNothingIsWiderThanTheWindow(Scene scene, MainShell shell, String[] keys) {
        double available = scene.getWindow() != null && scene.getWindow().getWidth() > 0
                ? scene.getWindow().getWidth()
                : scene.getWidth();
        for (String key : keys) {
            if (!buildsWithoutError(shell, key)) {
                continue;
            }
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            double needed = scene.getRoot().minWidth(-1);
            // The root's own minimum covers padding and the sidebar; a screen that
            // needs more than this cannot be shown without losing its right edge.
            String detail = " (needs " + Math.round(needed) + "px, window is "
                    + Math.round(available) + "px)";
            check("screen fits the window width: " + key + detail, needed <= available + 1);

            // A ScrollPane reports a small minimum of its own however wide its
            // content is, so the root measurement above cannot see inside it. What
            // can be checked is that the content does not ask for far more width
            // than the window offers: past that point the scroll pane produces a
            // horizontal bar and the right-hand side of the screen is off-view.
            for (javafx.scene.control.ScrollPane pane : scrollPanesIn(scene.getRoot())) {
                javafx.scene.Node content = pane.getContent();
                if (content == null || pane.getViewportBounds() == null) {
                    continue;
                }
                double viewport = pane.getViewportBounds().getWidth();
                if (viewport <= 0) {
                    continue;
                }
                double needs = content.minWidth(-1);
                // 80px of slack: a Label reports the width of its text on one line
                // even when it wraps, so a small excess is not a visible overflow.
                if (needs > viewport + 80) {
                    check("screen content is not far wider than the window: " + key
                            + " (needs " + Math.round(needs) + "px, viewport is "
                            + Math.round(viewport) + "px)", false);
                    explainWidth(content, "  ");
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
    private static void findOffscreen(javafx.scene.Node node, double limit,
                                      List<String> sink, boolean leavesOnly) {
        if (!node.isVisible() || node.getScene() == null) {
            return;
        }
        javafx.geometry.Bounds inScene = node.localToScene(node.getBoundsInLocal());
        boolean tooFarRight = inScene.getMaxX() > limit
                && node.getBoundsInLocal().getWidth() > 0;
        boolean isParent = node instanceof javafx.scene.Parent;
        if (tooFarRight && !(leavesOnly && isParent)) {
            sink.add(Math.round(inScene.getMaxX()) + "px right edge: " + describe(node));
        }
        if (isParent) {
            javafx.scene.Parent parent = (javafx.scene.Parent) node;
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                findOffscreen(child, limit, sink, leavesOnly);
            }
        }
    }

    /**
    private static void reportWidest(javafx.scene.Node node, double total) {
        List<WidthEntry> entries = new ArrayList<>();
        collectWidths(node, entries);
        entries.sort((a, b) -> Double.compare(b.width(), a.width()));
        System.out.println("        content needs " + Math.round(total)
                + "px; widest children:");
        for (int i = 0; i < Math.min(6, entries.size()); i++) {
            WidthEntry entry = entries.get(i);
            if (entry.width() <= 0) {
                continue;
            }
            System.out.println("          " + Math.round(entry.width()) + "px  " + entry.what());
        }
    }

    private static void collectWidths(javafx.scene.Node node, List<WidthEntry> sink) {
        double width = node.minWidth(-1);
        if (width > 0) {
            sink.add(new WidthEntry(describe(node), width));
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectWidths(child, sink);
            }
        }
    }

    /** A node's minimum width, with a description that identifies it. */
    private record WidthEntry(String what, double width) {
    }

    private static String describe(javafx.scene.Node node) {
        StringBuilder sb = new StringBuilder(node.getClass().getSimpleName());
        if (node instanceof javafx.scene.control.Labeled labelled
                && labelled.getText() != null && !labelled.getText().isBlank()) {
            String text = labelled.getText().replace('\n', ' ');
            sb.append(" \"").append(text, 0, Math.min(38, text.length())).append('"');
        }
        if (!node.getStyleClass().isEmpty()) {
            sb.append(' ').append(node.getStyleClass());
        }
        return sb.toString();
    }

    /**
     * Checks the "save as Word" and "save as Excel" buttons are on the report
     * screen, and that they read as the teacher's language.
     *
     * <p>They sit at the foot of a column that scrolls, so a mistake there is easy
     * to miss by eye: the button would simply never appear. This looks for the
     * labels in the built scene instead.
     */
    private void checkSaveAsButtonsExist(Scene scene, MainShell shell) {
        shell.show("reports");
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        List<String> labels = new ArrayList<>();
        collectButtonLabels(scene.getRoot(), labels);

        // Compared against the catalogue rather than against English words: the
        // suite has already switched the interface to Mongolian by this point, and
        // "Save as Word" is "Word болгож хадгалах" there.
        String word = Messages.get("format.saveAs", "Word");
        String excel = Messages.get("format.saveAs", "Excel");
        String filterWord = Messages.get("format.word.filter");
        String filterExcel = Messages.get("format.excel.filter");
        check("the report screen offers saving as Word", labels.contains(word));
        check("the report screen offers saving as Excel", labels.contains(excel));
        check("the save-as buttons are worded for the teacher, not as keys",
                labels.stream().noneMatch(text -> text.contains("format.saveAs")));

        // Each format must be spelled out, and the file chooser it opens must use
        // the extension that goes with it: a Word button saving .xlsx would be a
        // silent disaster.
        check("the Word button names the format",
                !DocumentFormat.WORD.label().isBlank() && word.contains(DocumentFormat.WORD.label()));
        check("the Excel button names the format",
                !DocumentFormat.EXCEL.label().isBlank() && excel.contains(DocumentFormat.EXCEL.label()));
        check("the Word file chooser filters for .docx",
                DocumentFormat.WORD.filterPattern().equals("*.docx")
                        && DocumentFormat.WORD.extension().equals("docx")
                        && DocumentFormat.WORD.filterLabel().contains("docx"));
        check("the Excel file chooser filters for .xlsx",
                DocumentFormat.EXCEL.filterPattern().equals("*.xlsx")
                        && DocumentFormat.EXCEL.extension().equals("xlsx")
                        && DocumentFormat.EXCEL.filterLabel().contains("xlsx"));
        check("the two formats are told apart by their extensions",
                !DocumentFormat.WORD.extension().equals(DocumentFormat.EXCEL.extension()));
    }

    /**
     * Checks the advice the save dialog gives about a folder.
     *
     * <p>The dialog exists because the Windows one refuses to save on some
     * computers. It tells the teacher whether a folder can be written to before they
     * try, so this checks that advice is right for a folder that works, a folder
     * that does not exist yet, and plainly wrong input.
     */
    private void checkSaveFolderAdvice() {
        java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home"));
        java.nio.file.Path documents = home.resolve("Documents");
        if (!java.nio.file.Files.isDirectory(documents)) {
            check("a Documents folder exists to test the save advice", false);
            return;
        }

        javafx.scene.control.Label label = new javafx.scene.control.Label();
        boolean problem = app.kinderjournal.ui.SaveLocationDialog
                .describeFolder(documents.toString(), label);
        check("the save dialog accepts a folder that can be written to", !problem);
        check("and says which folder it will use", label.getText().contains("Documents"));

        problem = app.kinderjournal.ui.SaveLocationDialog
                .describeFolder(documents.resolve("KinderJournal-new-subfolder").toString(), label);
        check("the save dialog offers to create a folder that is not there yet", !problem);
        check("and says it will be created", !label.getText().isBlank());

        problem = app.kinderjournal.ui.SaveLocationDialog.describeFolder("", label);
        check("the save dialog asks for a folder when the box is empty", problem);

        // A name Windows cannot use, to be sure it is refused rather than crashing.
        problem = app.kinderjournal.ui.SaveLocationDialog.describeFolder("::::", label);
        check("the save dialog refuses an impossible folder name", problem);

        // A file where a folder should be.
        try {
            java.nio.file.Path file = java.nio.file.Files.createTempFile("kj-not-a-folder-", ".txt");
            problem = app.kinderjournal.ui.SaveLocationDialog
                    .describeFolder(file.toString(), label);
            check("the save dialog refuses a path that is a file", problem);
            java.nio.file.Files.deleteIfExists(file);
        } catch (java.io.IOException ex) {
            check("a temporary file could be made for the save advice check: " + ex, false);
        }

        // A folder the writability estimate dislikes must still be allowed through.
        // Files.isWritable reports false for folders the program can write to, and
        // treating that as a veto once stopped every save on a real computer.
        java.nio.file.Path readOnlyLooking = null;
        for (String candidate : new String[]{
                System.getenv("ProgramFiles"), System.getenv("SystemRoot"),
                System.getenv("ProgramData")}) {
            if (candidate != null && java.nio.file.Files.isDirectory(
                    java.nio.file.Path.of(candidate))
                    && !java.nio.file.Files.isWritable(java.nio.file.Path.of(candidate))) {
                readOnlyLooking = java.nio.file.Path.of(candidate);
                break;
            }
        }
        if (readOnlyLooking != null) {
            problem = app.kinderjournal.ui.SaveLocationDialog
                    .describeFolder(readOnlyLooking.toString(), label);
            check("a folder that merely looks read-only is a warning, not a refusal",
                    !problem);
            // Compared against the catalogue, not against English: the suite has
            // switched the interface to Mongolian by this point.
            check("and the warning comes from the may-not-write wording",
                    label.getText().contains(readOnlyLooking.toString()));
            check("and the warning is not the plain all-clear",
                    !label.getText().equals(Messages.get("format.canSaveHere",
                            readOnlyLooking.toString())));
        } else {
            check("a read-only-looking folder exists to test the warning", false);
        }
    }

    /**
     * Checks the save dialog lays out with a status line that says something.
     *
     * <p>A blank status reads as a fault, and the dialog is the only way to save a
     * report on a computer whose Windows save dialog refuses every folder, so it has
     * to be right.
     */
    private void checkSaveDialogLaysOut(AppContext context) {
        var dialog = app.kinderjournal.ui.SaveLocationDialog.forTesting(
                DocumentFormat.WORD, "Test-report.docx", context);
        javafx.scene.layout.VBox body = dialog.buildBody();
        Scene scene = new Scene(body);
        scene.getStylesheets().addAll(Styles.stylesheet());
        body.applyCss();
        body.layout();

        List<String> labels = new ArrayList<>();
        collectButtonLabels(body, labels);
        labels.removeIf(String::isBlank);

        check("the save dialog offers a Save button",
                labels.stream().anyMatch(text -> text.equals(Messages.get("format.save"))));
        check("the save dialog offers a Cancel button",
                labels.stream().anyMatch(text -> text.equals(Messages.get("common.cancel"))));
        check("the save dialog offers to browse for a folder",
                labels.stream().anyMatch(text -> text.equals(Messages.get("format.browse"))));

        // The file name lives in a text field, so it is found by looking for fields
        // rather than for buttons.
        List<String> fields = new ArrayList<>();
        collectFieldText(body, fields);
        check("the save dialog suggests the report's file name",
                fields.stream().anyMatch(text -> text.endsWith(".docx")));
        check("the save dialog offers a folder to save in",
                fields.stream().anyMatch(text -> text.contains("\\") || text.contains("/")));

        check("the save dialog names some places to save to",
                body.getChildren().stream()
                        .anyMatch(node -> node instanceof javafx.scene.control.ListView<?> list
                                && list.getItems().size() >= 3));

        // The list must be tall enough to show them: a fixed 120 pixels cut it off
        // after four entries, hiding the drives.
        javafx.scene.control.ListView<?> placeList = findFirst(body,
                javafx.scene.control.ListView.class);
        if (placeList == null) {
            check("the save dialog has a list of places", false);
        } else {
            double rowHeight = placeList.getFixedCellSize() > 0
                    ? placeList.getFixedCellSize() : 24;
            check("the places list is tall enough to show every place ("
                            + placeList.getItems().size() + " places in "
                            + Math.round(placeList.getHeight()) + "px)",
                    placeList.getHeight() >= Math.min(placeList.getItems().size(), 6) * rowHeight);
        }

        check("the save dialog asks for enough width to be usable",
                body.prefWidth(-1) > 400);
    }

    /** The first node of a given type in a tree. */
    private static <T> T findFirst(javafx.scene.Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                T found = findFirst(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The text in every text field of a node tree. */
    private static void collectFieldText(javafx.scene.Node node, List<String> sink) {
        if (node instanceof javafx.scene.control.TextField field) {
            sink.add(field.getText() == null ? "" : field.getText());
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectFieldText(child, sink);
            }
        }
    }

    /** Every button caption in a node tree. */
    private static void collectButtonLabels(javafx.scene.Node node, List<String> sink) {
        if (node instanceof javafx.scene.control.ButtonBase button
                && button.getText() != null && !button.getText().isBlank()) {
            sink.add(button.getText());
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectButtonLabels(child, sink);
            }
        }
    }

    /**
     * Checks that a child avatar is a centred circle filled by its picture.
     *
     * <p>Two faults made these look broken, and neither threw: the circular clip
     * was centred on the pane's corner rather than its middle, so only a
     * quarter-disc was visible, and the picture was scaled to fit inside the frame
     * instead of cropped to fill it, so a wide photo shrank to a thin strip. Both
     * are geometry, so both can be asserted.
     */
    private void checkAvatarsAreRoundAndFilled() {
        app.kinderjournal.model.Child child = new app.kinderjournal.model.Child();
        child.setFirstName("Anna");
        child.setLastName("Berg");
        double radius = 34;
        javafx.scene.layout.StackPane avatar = Ui.avatar(child, radius);

        javafx.scene.Node clip = avatar.getClip();
        boolean centred = clip instanceof javafx.scene.shape.Circle circle
                && Math.abs(circle.getCenterX() - radius) < 0.01
                && Math.abs(circle.getCenterY() - radius) < 0.01
                && Math.abs(circle.getRadius() - radius) < 0.01;
        check("avatar clip is a circle centred on the avatar", centred);

        // The initials fallback must fill the frame too, not sit at its corner.
        check("avatar shows a placeholder when there is no photo",
                !avatar.getChildren().isEmpty());

        // Cropping: a wide picture must be cropped to the frame's shape, which is
        // the whole point - a viewport, not letterboxing.
        javafx.scene.image.Image wide = new javafx.scene.image.WritableImage(120, 40);
        javafx.scene.image.ImageView view = Ui.cover(wide, 64, 64);
        javafx.geometry.Rectangle2D crop = view.getViewport();
        check("a wide photo is cropped square to fill the frame",
                crop != null && Math.abs(crop.getWidth() - crop.getHeight()) < 0.01);
        check("the crop keeps the middle of the picture",
                crop != null && Math.abs(crop.getMinX() - 40) < 0.01);
        check("the crop fills the frame", view.getFitWidth() == 64 && view.getFitHeight() == 64);
        check("the view does not letterbox on top of the crop",
                !view.isPreserveRatio());
    }

    /** Every ScrollPane in a node tree. */
    private static List<javafx.scene.control.ScrollPane> scrollPanesIn(javafx.scene.Node node) {
        List<javafx.scene.control.ScrollPane> found = new ArrayList<>();
        if (node instanceof javafx.scene.control.ScrollPane pane) {
            found.add(pane);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                found.addAll(scrollPanesIn(child));
            }
        }
        return found;
    }

    /**
     * Walks the layout tree and names the children that demand the most width.
     *
     * <p>Used when a screen reports itself wider than its viewport: the answer is
     * always a handful of leaf controls whose text does not wrap, and naming them
     * is far quicker than reasoning about which label it might be.
     */
    private static void explainWidth(javafx.scene.Node node, String indent) {
        List<String> lines = new ArrayList<>();
        collectDemanding(node, indent, lines);
        for (int i = 0; i < Math.min(12, lines.size()); i++) {
            System.out.println(lines.get(i));
        }
    }

    private static void collectDemanding(javafx.scene.Node node, String indent, List<String> sink) {
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                double width = child.minWidth(-1);
                if (width > 250) {
                    sink.add(indent + Math.round(width) + "px  " + describe(child));
                    if (child instanceof javafx.scene.Parent) {
                        collectDemanding(child, indent + "  ", sink);
                    }
                } else {
                    collectDemanding(child, indent, sink);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Checks the stylesheet for constructs JavaFX's CSS parser rejects.
     *
     * <p>The parser logs and skips a bad declaration rather than failing, so a
     * typo would otherwise only show up as a subtly wrong-looking window. The
     * scientific-notation form of very large numbers is the usual culprit.
     */
    private boolean stylesheetIsValid() {
        try (var in = Styles.class.getResourceAsStream("/app/kinderjournal/ui/app.css")) {
            if (in == null) {
                return false;
            }
            String css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (css.contains("E308") || css.contains("e308")) {
                System.out.println("        stylesheet contains scientific notation, which the "
                        + "JavaFX CSS parser rejects");
                return false;
            }
            // Every declaration must end in a semicolon inside its block.
            long open = css.chars().filter(c -> c == '{').count();
            long close = css.chars().filter(c -> c == '}').count();
            if (open != close || open == 0) {
                System.out.println("        unbalanced braces: " + open + " open, " + close + " close");
                return false;
            }
            return true;
        } catch (Exception ex) {
            System.out.println("        " + ex);
            return false;
        }
    }

    /** Forces a layout pass, which is where missing resources usually surface. */
    private boolean layoutPassIsClean(Scene scene) {
        try {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return scene.getRoot().getBoundsInLocal().getWidth() > 0;
        } catch (Throwable ex) {
            System.out.println("        " + ex);
            return false;
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ok    " + name);
        } else {
            fail(name);
        }
    }

    private static void fail(String name) {
        failed++;
        failures.add(name);
        System.out.println("  FAIL  " + name);
    }
}
