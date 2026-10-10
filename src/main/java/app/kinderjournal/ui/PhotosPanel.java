package app.kinderjournal.ui;

import app.kinderjournal.model.Photo;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The photo strip attached to a record (an observation, an interview note or a
 * child's profile).
 *
 * <p>Pictures are copied into the local photos folder when added, so they keep
 * working even if the teacher later moves or deletes the original file. A tile
 * whose image has gone missing says so instead of showing a broken image, and
 * offers to re-attach a replacement.
 */
public final class PhotosPanel {

    private static final double TILE_WIDTH = 168;
    private static final double THUMB_HEIGHT = 116;

    private final AppContext context;
    private final Photo.OwnerType ownerType;
    private final long ownerId;
    private final long childId;
    private final javafx.stage.Window owner;
    private final java.util.function.Consumer<Photo> onOpen;
    private final Runnable onChanged;
    private final List<Photo> photos = new ArrayList<>();

    private VBox root;

    public PhotosPanel(AppContext context, Photo.OwnerType ownerType, long ownerId, long childId,
                       javafx.stage.Window owner, java.util.function.Consumer<Photo> onOpen,
                       Runnable onChanged) {
        this.context = context;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.childId = childId;
        this.owner = owner;
        this.onOpen = onOpen;
        this.onChanged = onChanged;
        reload();
    }

    /** Re-reads the attachments from the database. */
    public void reload() {
        photos.clear();
        photos.addAll(context.repo().photos().listFor(ownerType, ownerId));
        rebuild();
    }

    public Node node() {
        if (root == null) {
            rebuild();
        }
        return root;
    }

    private void rebuild() {
        root = new VBox(10);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(
                Ui.button("Add photos\u2026", this::addPhotos),
                Ui.meta(photos.size() == 1 ? "1 photo" : photos.size() + " photos"));
        root.getChildren().add(actions);

        if (photos.isEmpty()) {
            root.getChildren().add(Ui.emptyState("No photos attached",
                    "Add pictures from this computer. They are copied into the local data folder "
                            + "and are never uploaded anywhere.",
                    null));
            return;
        }

        FlowPane pane = new FlowPane(10, 10);
        for (Photo photo : photos) {
            pane.getChildren().add(tile(photo));
        }
        root.getChildren().add(pane);

        int missing = 0;
        for (Photo photo : photos) {
            if (!photo.fileExists()) {
                missing++;
            }
        }
        if (missing > 0) {
            HBox warning = new HBox(10);
            warning.getStyleClass().add("warning-banner");
            warning.setAlignment(Pos.CENTER_LEFT);
            warning.getChildren().add(new Label(missing == 1
                    ? "One attached image file is missing from the photos folder. It may have been "
                      + "moved or deleted outside the app. You can re-attach a replacement or remove "
                      + "the entry."
                    : missing + " attached image files are missing from the photos folder. You can "
                      + "re-attach replacements or remove the entries."));
            root.getChildren().add(warning);
        }
    }

    private Node tile(Photo photo) {
        VBox tile = new VBox(4);
        tile.getStyleClass().add("photo-tile");
        tile.setPrefWidth(TILE_WIDTH);
        tile.setMaxWidth(TILE_WIDTH);

        Image image = Ui.loadImage(photo.getFile(), TILE_WIDTH - 16);
        if (image == null) {
            Label missing = new Label("Image file missing");
            missing.getStyleClass().add("photo-missing");
            missing.setPrefSize(TILE_WIDTH - 16, THUMB_HEIGHT);
            missing.setAlignment(Pos.CENTER);
            missing.setWrapText(true);
            tile.getChildren().add(missing);
        } else {
            ImageView view = new ImageView(image);
            view.setFitWidth(TILE_WIDTH - 16);
            view.setFitHeight(THUMB_HEIGHT);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            Rectangle clip = new Rectangle(TILE_WIDTH - 16, THUMB_HEIGHT);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            view.setClip(clip);
            if (onOpen != null) {
                view.setCursor(javafx.scene.Cursor.HAND);
                view.setOnMouseClicked(event -> onOpen.accept(photo));
                javafx.scene.control.Tooltip.install(view,
                        new javafx.scene.control.Tooltip("Open this photo full size"));
            }
            tile.getChildren().add(view);
        }

        Label caption = new Label(Text.isBlank(photo.getCaption()) ? "(no caption)" : photo.getCaption());
        caption.getStyleClass().add(Text.isBlank(photo.getCaption()) ? "field-empty" : "meta");
        caption.setWrapText(true);
        caption.setMaxWidth(TILE_WIDTH - 16);
        tile.getChildren().add(caption);

        Label size = new Label(Text.joinNonBlank(" \u00b7 ", photo.sizeText(),
                photo.getAddedAt().isEmpty() ? "" : "added " + photo.getAddedAt().substring(0,
                        Math.min(10, photo.getAddedAt().length()))));
        size.getStyleClass().add("stat-hint");
        tile.getChildren().add(size);

        HBox buttons = new HBox(2,
                Ui.linkButton("Caption", () -> editCaption(photo)),
                photo.fileExists()
                        ? Ui.linkButton("Replace", () -> replace(photo))
                        : Ui.linkButton("Re-attach", () -> replace(photo)),
                Ui.linkButton("Remove", () -> remove(photo)));
        tile.getChildren().add(buttons);
        return tile;
    }

    private void editCaption(Photo photo) {
        String value = Ui.prompt("Photo caption", "Caption", photo.getCaption()).orElse(null);
        if (value == null) {
            return;
        }
        context.repo().photos().updateCaption(photo.getId(), value);
        reload();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    // ------------------------------------------------------------------ actions

    private void addPhotos() {
        List<File> files = chooseImages("Add photos");
        if (files == null || files.isEmpty()) {
            return;
        }
        String caption = "";
        if (files.size() == 1) {
            String value = Ui.prompt("Photo caption", "Describe the photo (optional)", "")
                    .orElse(null);
            if (value == null) {
                return;
            }
            caption = value;
        } else {
            if (!Ui.confirm("Add " + files.size() + " photos?",
                    "They will be attached to this record. You can add a caption to each one "
                            + "afterwards.", "Add them")) {
                return;
            }
        }
        int added = 0;
        List<String> problems = new ArrayList<>();
        for (File file : files) {
            try {
                context.repo().photos().add(ownerType, ownerId, childId, file.toPath(), caption);
                added++;
            } catch (Exception ex) {
                Ui.logError("Attaching " + file.getName(), ex);
                problems.add(file.getName());
            }
        }
        reload();
        if (onChanged != null) {
            onChanged.run();
        }
        if (!problems.isEmpty()) {
            Ui.error("Some photos could not be attached",
                    "These files could not be copied: " + String.join(", ", problems));
        } else if (added > 0) {
            Ui.info("Photos added", added == 1 ? "The photo was attached."
                    : added + " photos were attached.");
        }
    }

    private void replace(Photo photo) {
        List<File> files = chooseImages("Choose a replacement image");
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            context.repo().photos().delete(photo.getId());
            Photo replacement = context.repo().photos().add(ownerType, ownerId, childId,
                    files.get(0).toPath(), photo.getCaption());
            context.repo().photos().updateCaption(replacement.getId(), photo.getCaption());
            reload();
            if (onChanged != null) {
                onChanged.run();
            }
        } catch (Exception ex) {
            Ui.error("Could not replace this photo", ex);
        }
    }

    private void remove(Photo photo) {
        if (!Ui.confirmDestructive("Remove this photo?",
                "The picture is deleted from the local photos folder. This cannot be undone.",
                "Remove photo")) {
            return;
        }
        context.repo().photos().delete(photo.getId());
        reload();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private List<File> chooseImages(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        return chooser.showOpenMultipleDialog(owner);
    }

    // ------------------------------------------------------------------ viewing

    /**
     * Shows a photo full size in its own window, with the caption and the folder
     * it is stored in.
     */
    public static void showPhotoViewer(Photo photo, javafx.stage.Window owner) {
        Path file = AppPaths.photoFile(photo.getFile());
        Image image = file == null ? null : Ui.loadImage(photo.getFile(), 1200);

        javafx.stage.Stage window = new javafx.stage.Stage();
        window.initOwner(owner);
        window.setTitle(Text.isBlank(photo.getCaption()) ? "Photo" : photo.getCaption());

        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #1f2933;");

        if (image == null) {
            Label missing = new Label("This image file is missing.\n\nExpected at:\n" + file);
            missing.setStyle("-fx-text-fill: #ffffff;");
            missing.setWrapText(true);
            box.getChildren().add(missing);
        } else {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(Math.min(1100, image.getWidth()));
            view.setFitHeight(Math.min(760, image.getHeight()));
            box.getChildren().add(view);
            Label caption = new Label(Text.joinNonBlank(" \u00b7 ", photo.getCaption(),
                    photo.sizeText(), photo.getAddedAt()));
            caption.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
            caption.setWrapText(true);
            box.getChildren().add(caption);
        }
        box.getChildren().add(Ui.meta("Stored locally: " + file));

        javafx.scene.Scene scene = new javafx.scene.Scene(box, Color.web("#1f2933"));
        scene.getStylesheets().addAll(Styles.stylesheet());
        window.setScene(scene);
        window.show();
    }
}
