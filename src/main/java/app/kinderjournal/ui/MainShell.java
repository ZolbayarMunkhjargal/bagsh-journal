package app.kinderjournal.ui;

import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;
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

    /** One entry in the sidebar. The caption is looked up per language. */
    private record NavItem(String key, String glyph, String titleKey, String shortcut,
                           Function<AppContext, Refreshable> factory) {

        /** The caption in the language currently selected. */
        String label() {
            return Messages.get(titleKey);
        }
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

    /** Rebuilt when the language changes, so their captions can be replaced. */
    private final VBox sidebar = new VBox();
    private final HBox statusBar = new HBox(16);

    private Refreshable current;
    private String currentKey;

    public MainShell(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        registerScreens();

        root.getStyleClass().add("app-shell");
        sidebar.getStyleClass().add("sidebar");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        buildSidebar();
        root.setLeft(sidebar);
        root.setCenter(buildContent());
        buildStatusBar();
        root.setBottom(statusBar);

        context.onDataChanged(this::refreshCurrent);
        context.onGroupChanged(group -> {
            updateStatusGroup(group);
            refreshCurrent();
        });
        // Rebuilding the shell is how a language change takes effect: every label
        // is created from the catalogue, so new text means new nodes. The choice is
        // recorded here rather than in the button handler, so it is saved however
        // the language was changed - including from the Data screen's selector.
        Messages.onChange(() -> {
            context.repo().db().saveSetting("language", Messages.current().code());
            relanguage();
        });

        show("dashboard");
    }

    public Node root() {
        return root;
    }

    // ---------------------------------------------------------------- registry

    private void registerScreens() {
        navItems.add(new NavItem("dashboard", "\u25a6", "nav.dashboard", "Ctrl+1",
                ctx -> new DashboardScreen(ctx, this)));
        navItems.add(new NavItem("children", "\u263a", "nav.children", "Ctrl+2",
                ctx -> new ChildrenScreen(ctx, this)));
        navItems.add(new NavItem("classes", "\u25a4", "nav.classes", "Ctrl+3",
                ctx -> new ClassesScreen(ctx, this)));
        navItems.add(new NavItem("attendance", "\u2713", "nav.attendance", "Ctrl+4",
                ctx -> new AttendanceScreen(ctx, this)));
        navItems.add(new NavItem("observations", "\u270e", "nav.observations", "Ctrl+5",
                ctx -> new ObservationsScreen(ctx, this)));
        navItems.add(new NavItem("interviews", "\u260e", "nav.interviews", "Ctrl+6",
                ctx -> new InterviewsScreen(ctx, this)));
        navItems.add(new NavItem("history", "\u21bb", "nav.history", "Ctrl+7",
                ctx -> new HistoryScreen(ctx, this)));
        navItems.add(new NavItem("plans", "\u25a3", "nav.plans", "Ctrl+8",
                ctx -> new PlansScreen(ctx, this)));
        navItems.add(new NavItem("search", "\u2315", "nav.search", "Ctrl+F",
                ctx -> new SearchScreen(ctx, this)));
        navItems.add(new NavItem("reports", "\u2399", "nav.reports", "Ctrl+P",
                ctx -> new ReportsScreen(ctx, this)));
        navItems.add(new NavItem("data", "\u2691", "nav.data", "",
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

    /** Fills the sidebar in the current language; called again on a language change. */
    private void buildSidebar() {
        sidebar.getChildren().clear();
        navButtons.clear();

        VBox brand = new VBox(1);
        brand.getStyleClass().add("sidebar-brand");
        Label title = new Label(Messages.get("app.name"));
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label(Messages.get("app.tagline"));
        subtitle.getStyleClass().add("brand-subtitle");
        brand.getChildren().addAll(title, subtitle);
        sidebar.getChildren().add(brand);

        sidebar.getChildren().add(navSectionLabel(Messages.get("nav.section.overview")));
        sidebar.getChildren().add(navButton("dashboard"));
        sidebar.getChildren().add(navSectionLabel(Messages.get("nav.section.records")));
        for (String key : List.of("children", "classes", "attendance", "observations",
                "interviews", "history")) {
            sidebar.getChildren().add(navButton(key));
        }
        sidebar.getChildren().add(navSectionLabel(Messages.get("nav.section.planning")));
        sidebar.getChildren().add(navButton("plans"));
        sidebar.getChildren().add(navSectionLabel(Messages.get("nav.section.tools")));
        for (String key : List.of("search", "reports", "data")) {
            sidebar.getChildren().add(navButton(key));
        }

        sidebar.getChildren().add(Ui.vspacer());

        VBox footer = new VBox(4);
        footer.getStyleClass().add("sidebar-footer");
        Label teacher = new Label(teacherLine());
        teacher.setWrapText(true);
        Label tip = new Label(Messages.get("app.dataStaysLocal"));
        tip.setWrapText(true);
        footer.getChildren().addAll(teacher, tip);
        sidebar.getChildren().add(footer);

        // The sidebar is rebuilt on a language change, so the listener is replaced
        // rather than added again; the footer label is the only thing it updates.
        sidebarFooterTeacher = teacher;
        toolbarLanguageNote = tip;
    }

    private Label sidebarFooterTeacher;
    private Label toolbarLanguageNote;

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
        Button button = new Button(item.glyph() + "   " + Messages.get(item.titleKey()));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> show(key));
        if (!item.shortcut().isEmpty()) {
            button.setTooltip(new Tooltip(Messages.get("nav.tooltip",
                    Messages.get(item.titleKey()), item.shortcut())));
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

    private void buildStatusBar() {
        statusBar.getChildren().clear();
        statusGroup.setText(Messages.get("status.currentClass", context.currentGroupName()));
        statusGroup.setTooltip(new Tooltip(Messages.get("status.currentClassTooltip")));
        Label where = new Label(Messages.get("status.dataFolder", shortenedPath()));
        where.setTooltip(new Tooltip(context.repo().dataLocationText()));
        statusBar.getChildren().addAll(statusGroup, Ui.spacer(), where, buildLanguageToggle());
    }

    /**
     * The language button: shows the language it will switch to, in that
     * language's own script, so it reads correctly whichever one is active.
     */
    private Node buildLanguageToggle() {
        Lang target = Messages.current().other();
        Button button = new Button("\u25cf " + target.nativeName());
        button.getStyleClass().add("language-toggle");
        button.setTooltip(new Tooltip(Messages.get("language.tooltip", target.nativeName())));
        button.setOnAction(event -> changeLanguage(target));
        return button;
    }

    /** Switches language; the change listener records the choice and rebuilds. */
    private void changeLanguage(Lang target) {
        Messages.setCurrent(target);
    }

    /**
     * Rebuilds the whole window in the newly selected language.
     *
     * <p>Called through {@link Messages#onChange}, so it also runs if the language
     * is changed from somewhere other than the status bar.
     */
    private void relanguage() {
        buildSidebar();
        buildStatusBar();
        if (sidebarFooterTeacher != null) {
            sidebarFooterTeacher.setText(teacherLine());
        }
        // Every screen builds its labels in its constructor, so the current one is
        // discarded and rebuilt rather than patched label by label.
        if (currentKey != null) {
            show(currentKey);
        }
        if (stage != null) {
            stage.setTitle(Messages.get("app.windowTitle"));
        }
    }

    private void updateStatusGroup(Group group) {
        statusGroup.setText(Messages.get("status.currentClass",
                group == null ? Messages.get("status.noClassSelected") : group.getName()));
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
            Ui.error(Messages.get("error.couldNotOpen", item.label()), ex);
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
            stage.setTitle(Messages.get("app.windowTitle"));
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
            Ui.error(Messages.get("error.couldNotRefresh"), ex);
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
        Ui.warn(Messages.get("error.fallbackFolder"),
                Messages.get("error.fallbackFolderBody") + "\n\n"
                        + context.repo().dataLocationText() + "\n\n"
                        + Messages.get("error.fallbackFolderBody2"));
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
