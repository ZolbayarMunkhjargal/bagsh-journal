package app.kinderjournal.db;

import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.Observation;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes daily observations. */
public class ObservationDao {

    /** Shared projection so every query returns the same shape, with child/group names. */
    private static final String SELECT_BASE = """
            SELECT o.id, o.child_id, o.group_id, o.date, o.category, o.title, o.body, o.context,
                   o.author, o.confidential, o.created_at, o.updated_at,
                   (c.first_name || ' ' || c.last_name) AS child_name,
                   COALESCE((SELECT COUNT(*) FROM photos p
                             WHERE p.owner_type = 'observation' AND p.owner_id = o.id), 0) AS photo_count
            FROM observations o
            JOIN children c ON c.id = o.child_id
            """;

    private final Db db;

    public ObservationDao(Db db) {
        this.db = db;
    }

    private static Observation map(Row row) {
        Observation o = new Observation();
        o.setId(row.getLong("id"));
        o.setChildId(row.getLong("child_id"));
        o.setGroupId(row.getLong("group_id"));
        o.setDate(row.getString("date"));
        o.setCategory(row.getString("category"));
        o.setTitle(row.getString("title"));
        o.setBody(row.getString("body"));
        o.setContext(row.getString("context"));
        o.setAuthor(row.getString("author"));
        o.setConfidential(row.getBoolean("confidential"));
        o.setCreatedAt(row.getString("created_at"));
        o.setUpdatedAt(row.getString("updated_at"));
        o.setChildName(row.getString("child_name").trim());
        o.setPhotoCount(row.getInt("photo_count"));
        return o;
    }

    // ------------------------------------------------------------------- queries

    /** Observations for one child, newest first. Optionally narrowed by date and category. */
    public List<Observation> listForChild(long childId, LocalDate from, LocalDate to, String category) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE o.child_id = ?");
        params.add(childId);
        appendFilters(sql, params, null, from, to, category, null);
        sql.append(" ORDER BY o.date DESC, o.id DESC");
        return db.list(sql.toString(), ObservationDao::map, params.toArray());
    }

    /** Observations across a group, with the same optional filters. */
    public List<Observation> listForGroup(long groupId, LocalDate from, LocalDate to, String category) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        appendFilters(sql, params, groupId, from, to, category, null);
        sql.append(" ORDER BY o.date DESC, o.id DESC");
        return db.list(sql.toString(), ObservationDao::map, params.toArray());
    }

    /**
     * Observations across every child in the given list.
     * Used by class reports and by the dashboard feed.
     */
    public List<Observation> listForChildren(List<Child> children, LocalDate from, LocalDate to,
                                             String category, int limit) {
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> params = new ArrayList<>();
        String placeholders = String.join(",", children.stream().map(c -> "?").toList());
        StringBuilder sql = new StringBuilder(SELECT_BASE)
                .append("WHERE o.child_id IN (").append(placeholders).append(")");
        params.addAll(children.stream().map(Child::getId).toList());
        appendFilters(sql, params, null, from, to, category, null);
        sql.append(" ORDER BY o.date DESC, o.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return db.list(sql.toString(), ObservationDao::map, params.toArray());
    }

    /** The most recent observations anywhere, for the dashboard. */
    public List<Observation> listRecent(LocalDate from, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (from != null) {
            sql.append(" AND o.date >= ?");
            params.add(Dates.toIso(from));
        }
        sql.append(" ORDER BY o.date DESC, o.id DESC LIMIT ").append(Math.max(1, limit));
        return db.list(sql.toString(), ObservationDao::map, params.toArray());
    }

    /** Full-text search across the title and body. */
    public List<Observation> search(String keyword, Long childId, Long groupId,
                                    LocalDate from, LocalDate to, String category, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (!Text.isBlank(keyword)) {
            sql.append(" AND (LOWER(o.title) LIKE ? OR LOWER(o.body) LIKE ? OR LOWER(o.context) LIKE ?)");
            String like = "%" + keyword.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (childId != null && childId > 0) {
            sql.append(" AND o.child_id = ?");
            params.add(childId);
        }
        appendFilters(sql, params, groupId, from, to, category, null);
        sql.append(" ORDER BY o.date DESC, o.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return db.list(sql.toString(), ObservationDao::map, params.toArray());
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, Long groupId,
                                      LocalDate from, LocalDate to, String category, String ignored) {
        if (groupId != null && groupId > 0) {
            sql.append(" AND o.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND o.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND o.date <= ?");
            params.add(Dates.toIso(to));
        }
        if (!Text.isBlank(category) && !"all".equalsIgnoreCase(category)) {
            sql.append(" AND o.category = ?");
            params.add(category);
        }
    }

    public Observation find(long id) {
        return db.one(SELECT_BASE + "WHERE o.id = ?", ObservationDao::map, id);
    }

    /** How many observations exist for a child in a period (any category). */
    public int countForChild(long childId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS n FROM observations o WHERE o.child_id = ?");
        params.add(childId);
        if (from != null) {
            sql.append(" AND o.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND o.date <= ?");
            params.add(Dates.toIso(to));
        }
        return (int) db.count(sql.toString(), params.toArray());
    }

    /** Observation counts per category, keyed by category id. */
    public java.util.Map<String, Integer> countByCategory(long childId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT o.category AS category, COUNT(*) AS n FROM observations o WHERE 1 = 1");
        if (childId > 0) {
            sql.append(" AND o.child_id = ?");
            params.add(childId);
        }
        if (from != null) {
            sql.append(" AND o.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND o.date <= ?");
            params.add(Dates.toIso(to));
        }
        sql.append(" GROUP BY o.category");
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (var pair : db.list(sql.toString(),
                row -> java.util.Map.entry(row.getString("category"), row.getInt("n")),
                params.toArray())) {
            out.put(pair.getKey(), pair.getValue());
        }
        return out;
    }

    /** Count of observations written on a given day (dashboard). */
    public int countOn(LocalDate date) {
        return (int) db.count("SELECT COUNT(*) AS n FROM observations WHERE date = ?", Dates.toIso(date));
    }

    /** The date of the most recent observation anywhere, or null. */
    public String mostRecentDate() {
        return db.one("SELECT MAX(date) AS d FROM observations", row -> row.getString("d"));
    }

    // -------------------------------------------------------------------- writes

    public long insert(Observation o) {
        String now = Db.now();
        long id = db.insert("""
                INSERT INTO observations(child_id, group_id, date, category, title, body, context,
                                         author, confidential, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                o.getChildId(), o.getGroupId() == 0 ? null : o.getGroupId(), o.getDate(),
                o.getCategory(), o.getTitle(), o.getBody(), o.getContext(), o.getAuthor(),
                o.isConfidential(), now, now);
        o.setId(id);
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        return id;
    }

    public void update(Observation o) {
        String now = Db.now();
        db.update("""
                UPDATE observations SET child_id = ?, group_id = ?, date = ?, category = ?, title = ?,
                                        body = ?, context = ?, author = ?, confidential = ?, updated_at = ?
                WHERE id = ?""",
                o.getChildId(), o.getGroupId() == 0 ? null : o.getGroupId(), o.getDate(),
                o.getCategory(), o.getTitle(), o.getBody(), o.getContext(), o.getAuthor(),
                o.isConfidential(), now, o.getId());
        o.setUpdatedAt(now);
    }

    public void save(Observation o) {
        if (o.getId() == 0) {
            insert(o);
        } else {
            update(o);
        }
    }

    public void delete(long id) {
        db.run(() -> db.update("DELETE FROM observations WHERE id = ?", id));
    }

    // ------------------------------------------------------------------- history

    public List<HistoryEntry> historyForChild(long childId, LocalDate from, LocalDate to, String category) {
        List<HistoryEntry> out = new ArrayList<>();
        for (Observation o : listForChild(childId, from, to, category)) {
            Choices.ObservationCategory cat = o.categoryEnum();
            out.add(HistoryEntry.observation(o.getDate(), o.getId(),
                    Text.joinNonBlank(" \u00b7 ", cat.label(), o.getContext()),
                    o.preview(160), o.getBody(), cat.colour(), o.getPhotoCount()));
        }
        return out;
    }
}
