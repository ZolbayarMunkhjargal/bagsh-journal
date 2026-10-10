package app.kinderjournal.report;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders generated report HTML as JavaFX nodes for the print preview.
 *
 * <p>This is a deliberately small renderer: it understands exactly the elements
 * {@link ReportRenderer} produces - headings, paragraphs, tables, entry blocks and
 * embedded photos - and ignores anything else. It is not a general HTML engine,
 * which is why the preview and the printed document are guaranteed to carry the
 * same content.
 */
public final class HtmlPreview {

    private static final Pattern BODY = Pattern.compile("(?s)<body[^>]*>(.*)</body>");
    private static final Pattern TAG = Pattern.compile("(?s)<(/?)([a-zA-Z0-9]+)([^>]*)>");
    private static final Pattern IMG_SRC = Pattern.compile("src=\"([^\"]*)\"");
    private static final Pattern CLASS_ATTR = Pattern.compile("class=\"([^\"]*)\"");

    /** Parses the HTML and appends the rendered nodes to {@code target}. */
    public void renderInto(VBox target, String html) {
        String body = extractBody(html);
        // The page setup and stylesheet are irrelevant to the preview.
        body = body.replaceAll("(?s)<style.*?</style>", "");
        List<Node> nodes = renderFragment(body);
        target.getChildren().addAll(nodes);
    }

    private String extractBody(String html) {
        Matcher matcher = BODY.matcher(html);
        return matcher.find() ? matcher.group(1) : html;
    }

    // --------------------------------------------------------------- rendering

    private List<Node> renderFragment(String fragment) {
        List<Node> out = new ArrayList<>();
        Matcher matcher = TAG.matcher(fragment);
        int cursor = 0;
        String currentBlock = null;
        StringBuilder text = new StringBuilder();
        List<String> tableRows = null;
        String row = null;

        while (matcher.find()) {
            String literal = fragment.substring(cursor, matcher.start());
            cursor = matcher.end();
            String closing = matcher.group(1);
            String name = matcher.group(2).toLowerCase();
            String attributes = matcher.group(3) == null ? "" : matcher.group(3);

            switch (name) {
                case "h1", "h2", "h3", "p", "div", "strong", "span", "br", "img", "ul", "li" -> {
                    // handled below
                }
                case "table" -> {
                    if (closing.isEmpty()) {
                        tableRows = new ArrayList<>();
                    } else if (tableRows != null) {
                        out.add(buildTable(tableRows));
                        tableRows = null;
                    }
                    continue;
                }
                case "tr" -> {
                    if (closing.isEmpty()) {
                        row = "";
                    } else if (tableRows != null && row != null) {
                        tableRows.add(row);
                        row = null;
                    }
                    continue;
                }
                case "td", "th" -> {
                    if (closing.isEmpty()) {
                        currentBlock = name.equals("th") ? "th" : "td";
                        text.setLength(0);
                    } else {
                        if (row != null) {
                            row = row + "\u0001" + text.toString().trim();
                        }
                        currentBlock = null;
                    }
                    continue;
                }
                default -> {
                    continue;
                }
            }

            // Opening and closing tags for the block-level elements we care about.
            if (name.equals("img")) {
                String src = attribute(IMG_SRC, attributes, 1);
                Node image = buildImage(src);
                if (image != null) {
                    if (tableRows != null && row != null) {
                        row = row + "\u0001[photo]";
                    } else {
                        out.add(image);
                    }
                }
                continue;
            }
            if (name.equals("br")) {
                text.append('\n');
                continue;
            }
            if (closing.isEmpty()) {
                if (currentBlock == null) {
                    currentBlock = name;
                    text.setLength(0);
                }
                // A nested opening tag inside a block keeps the outer block type.
                if (name.equals("li")) {
                    text.append("\u2022 ");
                }
            } else {
                if (currentBlock == null) {
                    continue;
                }
                String value = text.toString().trim();
                if (name.equals("strong") || name.equals("span") || name.equals("li")
                        || name.equals("ul")) {
                    continue; // closing an inline element inside a block
                }
                if (tableRows != null && row != null) {
                    row = row + "\u0001" + value;
                } else if (!value.isEmpty()) {
                    Node block = buildBlock(currentBlock, value, attributes);
                    if (block != null) {
                        out.add(block);
                    }
                }
                currentBlock = null;
                text.setLength(0);
            }
            text.append(unescape(literal));
        }

        if (tableRows != null && !tableRows.isEmpty()) {
            out.add(buildTable(tableRows));
        }
        String tail = unescape(fragment.substring(cursor)).trim();
        if (!tail.isEmpty()) {
            out.add(bodyLabel(tail));
        }
        return out;
    }

    private Node buildBlock(String tag, String value, String attributes) {
        String classes = attribute(CLASS_ATTR, attributes, 1);
        return switch (tag) {
            case "h1" -> heading(value, 20, "#0f3d3e");
            case "h2" -> {
                VBox box = new VBox(2);
                Label label = heading(value, 14, "#0f3d3e");
                javafx.scene.control.Separator separator = new javafx.scene.control.Separator();
                box.getChildren().addAll(label, separator);
                box.setPadding(new Insets(14, 0, 4, 0));
                yield box;
            }
            case "h3" -> heading(value, 12.5, "#37474f");
            case "li" -> {
                Label label = bodyLabel(value);
                label.setPadding(new Insets(0, 0, 2, 12));
                yield label;
            }
            default -> {
                if (classes != null && classes.contains("entry-kind")) {
                    Label label = bodyLabel(value.toUpperCase());
                    label.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #52606d;");
                    yield label;
                }
                if (classes != null && classes.contains("entry-date")) {
                    Label label = new Label(value);
                    label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                    label.setStyle("-fx-text-fill: #0f3d3e;");
                    yield label;
                }
                if (classes != null && classes.contains("meta-line")
                        || classes != null && classes.contains("footer-note")) {
                    Label label = bodyLabel(value);
                    label.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
                    yield label;
                }
                if (classes != null && classes.contains("entry")) {
                    VBox block = new VBox(3, bodyLabel(value));
                    block.setPadding(new Insets(0, 0, 6, 10));
                    block.setStyle("-fx-border-color: transparent transparent transparent #90a4ae;"
                            + " -fx-border-width: 0 0 0 2;");
                    yield block;
                }
                yield bodyLabel(value);
            }
        };
    }

    private Label heading(String text, double size, String colour) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, size));
        label.setStyle("-fx-text-fill: " + colour + ";");
        label.setWrapText(true);
        return label;
    }

    private Label bodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #1f2933;");
        return label;
    }

    private Node buildImage(String src) {
        if (src == null || !src.startsWith("data:")) {
            return null;
        }
        int comma = src.indexOf(',');
        if (comma < 0) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
            Image image = new Image(new java.io.ByteArrayInputStream(bytes));
            if (image.isError()) {
                return null;
            }
            ImageView view = new ImageView(image);
            view.setFitWidth(Math.min(320, image.getWidth()));
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** A visual table from the "cell\u0001cell" rows collected while parsing. */
    private Node buildTable(List<String> rows) {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-border-color: #b0bec5; -fx-border-width: 1;");

        int maxColumns = 0;
        for (String row : rows) {
            maxColumns = Math.max(maxColumns, row.split("\u0001", -1).length);
        }
        for (int i = 0; i < maxColumns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / Math.max(1, maxColumns));
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String[] cells = rows.get(rowIndex).split("\u0001", -1);
            boolean header = rowIndex == 0;
            for (int column = 0; column < cells.length; column++) {
                String value = cells[column].trim();
                Node cell;
                if (value.equals("[photo]")) {
                    cell = new Label("\u25a3 photo");
                } else {
                    Label label = new Label(value);
                    label.setWrapText(true);
                    label.setStyle("-fx-font-size: 11px; -fx-text-fill: #1f2933;");
                    label.setMaxWidth(Double.MAX_VALUE);
                    cell = label;
                }
                VBox wrapper = new VBox(cell);
                wrapper.setPadding(new Insets(3, 5, 3, 5));
                wrapper.setStyle("-fx-border-color: transparent #b0bec5 #b0bec5 transparent;"
                        + " -fx-border-width: 0 1 1 0;"
                        + (header ? " -fx-background-color: #eceff1;" : ""));
                grid.add(wrapper, column, rowIndex);
            }
        }
        VBox holder = new VBox(grid);
        holder.setPadding(new Insets(6, 0, 10, 0));
        return holder;
    }

    private static String attribute(Pattern pattern, String attributes, int group) {
        Matcher matcher = pattern.matcher(attributes);
        return matcher.find() ? matcher.group(group) : null;
    }

    /** Turns the small set of entities the renderer emits back into characters. */
    static String unescape(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String value = html;
        value = value.replace("&nbsp;", " ");
        value = value.replace("&#39;", "'");
        value = value.replace("&quot;", "\"");
        value = value.replace("&lt;", "<");
        value = value.replace("&gt;", ">");
        value = value.replace("&middot;", "\u00b7");
        value = value.replace("&ndash;", "\u2013");
        value = value.replace("&mdash;", "\u2014");
        value = value.replace("&amp;", "&");
        // Collapse the indentation the renderer adds around block tags.
        return value.replaceAll("[ \\t]+\\n", "\n").trim();
    }

    /** Convenience for one-off rendering, used by tests and the About screen. */
    public static Node render(String html) {
        VBox box = new VBox(6);
        new HtmlPreview().renderInto(box, html);
        return box;
    }

    /** A short, plain-text version of the document, used for logs and tooltips. */
    public static String plainText(String html) {
        Matcher body = BODY.matcher(html);
        String content = body.find() ? body.group(1) : html;
        return unescape(content.replaceAll("(?s)<style.*?</style>", "")
                .replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
    }
}
