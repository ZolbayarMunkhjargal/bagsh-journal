package app.kinderjournal.ui;

import app.kinderjournal.model.Group;
import app.kinderjournal.ui.screen.AttendanceScreen;
import app.kinderjournal.ui.screen.ChildrenScreen;
import app.kinderjournal.ui.screen.ClassesScreen;
import app.kinderjournal.ui.screen.DashboardScreen;
import app.kinderjournal.ui.screen.DataScreen;
import app.kinderjournal.ui.screen.HistoryScreen;
import app.kinderjournal.ui.screen.InterviewsScreen;
import app.kinderjournal.ui.screen.ObservationsScreen;
import app.kinderjournal.ui.screen.PlansScreen;
import app.kinderjournal.ui.screen.ReportsScreen;
import app.kinderjournal.ui.screen.SearchScreen;
import app.kinderjournal.util.Text;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The application window: a navigation sidebar on the left, the active screen on
 * the right, and a status bar that always shows which class is selected and where
 * the data is stored.
 */
public final class MainShell {

    /** One entry in the sidebar. */
    private record NavItem(String key, String glyph, String label, String shortcut,
                           Function<AppContext, Refreshable> factory) {
    }

    private final AppContext context;
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final StackPane contentHost = new StackPane();
    private final Label headerTitle = new Label();
    private final Label headerSubtitle = new Label();
    private final HBox headerActions = new HBox(8);
    private final Label statusGroup = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final List<NavItem> navItems = new ArrayList<>();

    private Refreshable current;
    private String currentKey;

    public MainShell(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        registerScreens();
        root.getStyleClass().add("app-shell");
        root.setLeft(buildSidebar());
        root.setCenter(buildContent());
        root.setBottom(buildStatusBar());

        context.onDataChanged(this::refreshCurrent);
        context.onGroupChanged(group -> {
            updateStatusGroup(group);
            refreshCurrent();
        });

        show("dashboard");
    }

    public Node root() {
        return root;
    }

    // ---------------------------------------------------------------- registry

    private void registerScreens() {
        navItems.add(new NavItem("dashboard", "\u25a6", "Dashboard", "Ctrl+1",
                ctx -> new DashboardScreen(ctx, this)));
        navItems.add(new NavItem("children", "\u263a", "Children", "Ctrl+2",
                ctx -> new ChildrenScreen(ctx, this)));
        navItems.add(new NavItem("classes", "\u25a4", "Classes", "Ctrl+3",
                ctx -> new ClassesScreen(ctx, this)));
        navItems.add(new NavItem("attendance", "\u2713", "Attendance", "Ctrl+4",
                ctx -> new AttendanceScreen(ctx, this)));
        navItems.add(new NavItem("observations", "\u270e", "Observations", "Ctrl+5",
                ctx -> new ObservationsScreen(ctx, this)));
        navItems.add(new NavItem("interviews", "\u260e", "Interviews", "Ctrl+6",
                ctx -> new InterviewsScreen(ctx, this)));
        navItems.add(new NavItem("history", "\u21bb", "Child history", "Ctrl+7",
                ctx -> new HistoryScreen(ctx, this)));
        navItems.add(new NavItem("plans", "\u25a3", "Lesson plans", "Ctrl+8",
                ctx -> new PlansScreen(ctx, this)));
        navItems.add(new NavItem("search", "\u2315", "Search", "Ctrl+F",
                ctx -> new SearchScreen(ctx, this)));
        navItems.add(new NavItem("reports", "\u2399", "Print & reports", "Ctrl+P",
                ctx -> new ReportsScreen(ctx, this)));
        navItems.add(new NavItem("data", "\u2691", "Data & backup", "",
                ctx -> new DataScreen(ctx, this)));
    }

    private NavItem item(String key) {
        for (NavItem candidate : navItems) {
            if (candidate.key().equals(key)) {
                return candidate;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- sidebar

    private Node buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        VBox brand = new VBox(1);
        brand.getStyleClass().add("sidebar-brand");
        Label title = new Label(Ui.APP_NAME);
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Kindergarten journal");
        subtitle.getStyleClass().add("brand-subtitle");
        brand.getChildren().addAll(title, subtitle);
        sidebar.getChildren().add(brand);

        sidebar.getChildren().add(navSectionLabel("Overview"));
        sidebar.getChildren().add(navButton("dashboard"));
        sidebar.getChildren().add(navSectionLabel("Records"));
        for (String key : List.of("children", "classes", "attendance", "observations",
                "interviews", "history")) {
            sidebar.getChildren().add(navButton(key));
        }
        sidebar.getChildren().add(navSectionLabel("Planning"));
        sidebar.getChildren().add(navButton("plans"));
        sidebar.getChildren().add(navSectionLabel("Tools"));
        for (String key : List.of("search", "reports", "data")) {
            sidebar.getChildren().add(navButton(key));
        }

        sidebar.getChildren().add(Ui.vspacer());

        VBox footer = new VBox(4);
        footer.getStyleClass().add("sidebar-footer");
        Label teacher = new Label(teacherLine());
        teacher.setWrapText(true);
        Label tip = new Label("All data stays on this computer");
        tip.setWrapText(true);
        footer.getChildren().addAll(teacher, tip);
        sidebar.getChildren().add(footer);

        context.onGroupChanged(group -> teacher.setText(teacherLine()));
        return sidebar;
    }

    private String teacherLine() {
        String name = context.teacherName();
        String group = context.currentGroupName();
        return Text.isBlank(name) ? group : name + " \u00b7 " + group;
    }

    private Label navSectionLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.getStyleClass().add("nav-section");
        return label;
    }

    private Button navButton(String key) {
        NavItem item = item(key);
        Button button = new Button(item.glyph() + "   " + item.label());
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> show(key));
        if (!item.shortcut().isEmpty()) {
            button.setTooltip(new Tooltip(item.label() + "  (" + item.shortcut() + ")"));
        }
        navButtons.put(key, button);
        return button;
    }

    // ---------------------------------------------------------------- content

    private Node buildContent() {
        BorderPane content = new BorderPane();
        content.getStyleClass().add("content-area");

        HBox header = new HBox(12);
        header.getStyleClass().add("content-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(1);
        headerTitle.getStyleClass().add("screen-title");
        headerSubtitle.getStyleClass().add("screen-subtitle");
        titles.getChildren().addAll(headerTitle, headerSubtitle);
        HBox.setHgrow(titles, Priority.ALWAYS);

        headerActions.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().addAll(titles, headerActions);

        contentHost.setAlignment(Pos.TOP_LEFT);
        content.setTop(header);
        content.setCenter(contentHost);
        return content;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        statusGroup.setText("\u25cf " + context.currentGroupName());
        statusGroup.setTooltip(new Tooltip("The class currently being worked on"));
        Label where = new Label("Data folder: " + shortenedPath());
        where.setTooltip(new Tooltip(context.repo().dataLocationText()));
        bar.getChildren().addAll(statusGroup, Ui.spacer(), where);
        return bar;
    }

    private void updateStatusGroup(Group group) {
        statusGroup.setText("\u25cf " + (group == null ? "No class selected" : group.getName()));
    }

    private String shortenedPath() {
        String path = context.repo().dataLocationText();
        return path.length() > 60 ? "\u2026" + path.substring(path.length() - 58) : path;
    }

    // ---------------------------------------------------------------- navigate

    /** Switches to a screen by key, building it fresh. */
    public void show(String key) {
        NavItem item = item(key);
        if (item == null) {
            return;
        }
        Refreshable view;
        try {
            view = item.factory().apply(context);
        } catch (RuntimeException ex) {
            Ui.error("Could not open " + item.label(), ex);
            return;
        }
        currentKey = key;
        current = view;

        applyHeader(view);
        contentHost.getChildren().setAll(view.node());

        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("active");
            if (entry.getKey().equals(key)) {
                entry.getValue().getStyleClass().add("active");
            }
        }
        if (stage != null) {
            stage.setTitle(Ui.APP_NAME + " \u2014 " + headerTitle.getText());
        }
    }

    /**
     * Moves to a screen and then runs an action on it.
     *
     * <p>This is how a shortcut elsewhere ("record attendance", "add an
     * observation") lands the teacher directly on the right form.
     */
    public void showAnd(String key, Consumer<Refreshable> afterBuild) {
        show(key);
        if (afterBuild != null && current != null) {
            afterBuild.accept(current);
        }
    }

    /** Rebuilds the screen that is currently displayed. */
    public void refreshCurrent() {
        if (current == null) {
            if (currentKey != null) {
                show(currentKey);
            }
            return;
        }
        try {
            current.refresh();
            applyHeader(current);
            contentHost.getChildren().setAll(current.node());
        } catch (RuntimeException ex) {
            Ui.error("Could not refresh this screen", ex);
        }
    }

    /** Rebuilds only if the given screen is the one on display. */
    public void refreshIfShowing(String key) {
        if (key != null && key.equals(currentKey)) {
            refreshCurrent();
        }
    }

    /** How many nodes the content area is currently showing; used by the smoke test. */
    public int visibleContentNodeCount() {
        return contentHost.getChildren().size();
    }

    /** The key of the screen on display, for callers that need to know. */
    public String currentKey() {
        return currentKey;
    }

    private void applyHeader(Refreshable view) {
        String title = view.title();
        String subtitle = view.subtitle();
        headerTitle.setText(title);
        headerSubtitle.setText(subtitle);
        boolean hasSubtitle = !Text.isBlank(subtitle);
        headerSubtitle.setVisible(hasSubtitle);
        headerSubtitle.setManaged(hasSubtitle);

        headerActions.getChildren().clear();
        Node actions = view instanceof ScreenActions screenActions ? screenActions.headerActions() : null;
        if (actions != null) {
            headerActions.getChildren().add(actions);
        }
    }

    /** Extra buttons a screen wants shown in the shared header bar. */
    public interface ScreenActions {
        Node headerActions();
    }

    // ---------------------------------------------------------------- shortcuts

    /** Keyboard shortcuts installed on the scene. */
    public void handleShortcut(KeyEvent event) {
        if (!event.isControlDown()) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.F) {
            show("search");
        } else if (code == KeyCode.P) {
            show("reports");
        } else if (code == KeyCode.R) {
            refreshCurrent();
        } else if (code == KeyCode.D) {
            show("dashboard");
        } else if (code != null && code.isDigitKey()) {
            String name = code.getName();
            boolean matched = false;
            for (NavItem candidate : navItems) {
                if (candidate.shortcut().equals("Ctrl+" + name)) {
                    show(candidate.key());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return;
            }
        } else {
            return;
        }
        event.consume();
    }

    public AppContext context() {
        return context;
    }

    /** The dialog owner, so child dialogs sit above the main window. */
    public javafx.stage.Window window() {
        return stage;
    }

    // ---------------------------------------------------------------- reminders

    /** Warns once at start-up when the preferred data folder was not writable. */
    public void warnIfDataFolderIsFallback(Stage owner) {
        if (!app.kinderjournal.util.AppPaths.isUsingFallback()) {
            return;
        }
        Ui.warn("Using a fallback data folder",
                "KinderJournal could not write to your usual application data folder, so it is "
                        + "keeping its data here instead:\n\n"
                        + context.repo().dataLocationText()
                        + "\n\nEverything still works, but remember this location when making backups.");
    }

    /** The class selector widget, reused on screens that need one. */
    public static ComboBox<Group> groupSelector(AppContext context) {
        ComboBox<Group> combo = new ComboBox<>();
        combo.getItems().setAll(context.repo().groups().listActive());
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group group) {
                return group == null ? "" : group.getName();
            }

            @Override
            public Group fromString(String text) {
                return null;
            }
        });
        combo.setValue(context.currentGroup());
        combo.setPrefWidth(220);
        combo.valueProperty().addListener((observable, previous, next) -> {
            if (next != null) {
                context.setCurrentGroup(next);
            }
        });
        context.onGroupChanged(group -> combo.setValue(group));
        return combo;
    }
}
