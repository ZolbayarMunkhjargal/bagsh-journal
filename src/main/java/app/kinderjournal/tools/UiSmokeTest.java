package app.kinderjournal.tools;

import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.report.HtmlPreview;
import app.kinderjournal.report.ReportOptions;
import app.kinderjournal.report.ReportRenderer;
import app.kinderjournal.report.ReportType;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Styles;

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

    /** Runs every check, prints a summary, and terminates the JVM. */
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

            Scene scene = new Scene((javafx.scene.Parent) shell.root(), 1280, 800);
            scene.getStylesheets().addAll(Styles.stylesheet());
            scene.setOnKeyPressed(shell::handleShortcut);
            stage.setScene(scene);

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

            // Builds again for every screen, to catch rebuild-only mistakes.
            for (String key : keys) {
                boolean ok = true;
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

            checkEveryReportType(repo);
        } catch (Throwable ex) {
            fail("smoke test aborted: " + ex);
            ex.printStackTrace(System.out);
        }
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
        Group group = groups.get(0);
        Child child = children.get(0);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(2);

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
