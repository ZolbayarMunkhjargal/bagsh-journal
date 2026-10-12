package app.kinderjournal.tools;

import app.kinderjournal.db.Repository;
import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.report.ReportOptions;
import app.kinderjournal.report.ReportRenderer;
import app.kinderjournal.report.ReportType;
import app.kinderjournal.ui.AppContext;
import app.kinderjournal.ui.MainShell;
import app.kinderjournal.ui.Styles;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Opens the real window on sample data so a person can look at it.
 *
 * <p>Used to check that the interface - and particularly a non-Latin
 * translation - actually renders: fonts, layout and line wrapping cannot be
 * judged from assertions alone. It also writes one report per language, so the
 * printed output can be compared side by side.
 *
 * <p>Launched through {@code App --show <language code>}, which sets the system
 * properties read in {@link #start}.
 */
public final class DemoWindow extends Application {

    /** System property: the language code to open the window in. */
    public static final String PROPERTY_LANGUAGE = "kinderjournal.show.language";

    /** System property: where to keep the sample data. */
    public static final String PROPERTY_FOLDER = "kinderjournal.show.folder";

    /** System property: which screen to open on, e.g. {@code reports}. */
    public static final String PROPERTY_SCREEN = "kinderjournal.show.screen";

    /** System property: write a PNG of the window to this path, then exit. */
    public static final String PROPERTY_SNAPSHOT = "kinderjournal.show.snapshot";

    @Override
    public void start(Stage stage) {
        String code = System.getProperty(PROPERTY_LANGUAGE, Lang.ENGLISH.code());
        Path folder = Path.of(System.getProperty(PROPERTY_FOLDER,
                Path.of(System.getProperty("java.io.tmpdir"), "KinderJournal-language-check")
                        .toString()));
        try {
            Files.createDirectories(folder);
            System.setProperty("kinderjournal.dataDir", folder.toAbsolutePath().toString());
            // A known set of records, so no screen comes up empty.
            DemoData.seed(folder);
        } catch (java.io.IOException | RuntimeException ex) {
            System.out.println("Could not prepare the sample data: " + ex.getMessage());
        }

        Repository repository = Repository.open();
        repository.db().saveSetting("language", code);

        AppContext context = new AppContext(repository);
        MainShell shell = new MainShell(context, stage);

        Scene scene = new Scene((javafx.scene.Parent) shell.root(), 1150, 800);
        scene.getStylesheets().addAll(Styles.stylesheet());
        stage.setScene(scene);
        stage.setTitle(Messages.get("app.windowTitle"));
        stage.setX(60);
        stage.setY(30);
        stage.setOnHidden(event -> repository.close());
        stage.show();

        System.out.println("Window shown in " + Messages.current().englishName()
                + " (" + Messages.current().code() + ")");
        System.out.println("Data folder: " + folder.toAbsolutePath());
        writeSampleReports(repository, folder);

        // Opening straight on a screen is what makes a visual check of one
        // particular layout possible without clicking through the program.
        String screen = System.getProperty(PROPERTY_SCREEN, "");
        if (!screen.isBlank()) {
            shell.show(screen);
            System.out.println("Screen: " + screen);
        }

        // A snapshot rather than a screen grab: it captures the application's own
        // pixels, so checking the layout never photographs whatever else the
        // person using the computer happens to have open.
        String snapshot = System.getProperty(PROPERTY_SNAPSHOT, "");
        if (!snapshot.isBlank()) {
            javafx.application.Platform.runLater(() -> {
                try {
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    javafx.scene.image.WritableImage image = scene.snapshot(null);
                    java.nio.file.Path target = java.nio.file.Path.of(snapshot);
                    java.nio.file.Files.createDirectories(target.toAbsolutePath().getParent());
                    javax.imageio.ImageIO.write(toBufferedImage(image), "png", target.toFile());
                    System.out.println("Snapshot written to " + target.toAbsolutePath());
                } catch (Exception ex) {
                    System.out.println("Could not write the snapshot: " + ex);
                }
                javafx.application.Platform.exit();
                System.exit(0);
            });
        }
    }

    /**
     * Copies a JavaFX image into a {@link java.awt.image.BufferedImage}.
     *
     * <p>Done by hand rather than with {@code SwingFXUtils}, which would drag the
     * whole javafx.swing module into the runtime for the sake of a developer tool.
     */
    private static java.awt.image.BufferedImage toBufferedImage(javafx.scene.image.Image image) {
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    /** Writes a child-history report per language, so the printed pages can be compared. */
    private static void writeSampleReports(Repository repository, Path folder) {
        var groups = repository.groups().listActive();
        var children = repository.children().listAll();
        if (groups.isEmpty() || children.isEmpty()) {
            return;
        }
        ReportRenderer renderer = new ReportRenderer(repository);
        LocalDate to = LocalDate.now();
        Path out = folder.resolve("printed-samples");
        Lang original = Messages.current();
        try {
            Files.createDirectories(out);
            for (Lang lang : Lang.values()) {
                Messages.setCurrent(lang);
                ReportOptions options = ReportOptions.builder(ReportType.CHILD_HISTORY)
                        .group(groups.get(0).getId())
                        .child(children.get(0).getId())
                        .range(to.minusMonths(3), to)
                        .photos(true)
                        .confidential(true)
                        .build();
                Path file = out.resolve("history-" + lang.code() + ".html");
                Files.writeString(file, renderer.render(options).html());
                System.out.println("  wrote " + file.getFileName());
            }
        } catch (Exception ex) {
            System.out.println("  could not write the sample reports: " + ex);
        } finally {
            Messages.setCurrent(original);
        }
    }
}
