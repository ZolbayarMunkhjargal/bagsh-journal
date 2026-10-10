package app.kinderjournal.db;

import app.kinderjournal.model.Child;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.Interview;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes interview notes. */
public class InterviewDao {

    private static final String SELECT_BASE = """
            SELECT i.id, i.child_id, i.group_id, i.date, i.type, i.mode, i.participants, i.topics,
                   i.discussed, i.conclusions, i.follow_up, i.follow_up_date, i.follow_up_done,
                   i.created_at, i.updated_at,
                   (c.first_name || ' ' || c.last_name) AS child_name,
                   COALESCE((SELECT COUNT(*) FROM photos p
                             WHERE p.owner_type = 'interview' AND p.owner_id = i.id), 0) AS photo_count
            FROM interviews i
            JOIN children c ON c.id = i.child_id
            """;

    private final Db db;

    public InterviewDao(Db db) {
        this.db = db;
    }

    private static Interview map(Row row) {
        Interview i = new Interview();
        i.setId(row.getLong("id"));
        i.setChildId(row.getLong("child_id"));
        i.setGroupId(row.getLong("group_id"));
        i.setDate(row.getString("date"));
        i.setType(row.getString("type"));
        i.setMode(row.getString("mode"));
        i.setParticipants(row.getString("participants"));
        i.setTopics(row.getString("topics"));
        i.setDiscussed(row.getString("discussed"));
        i.setConclusions(row.getString("conclusions"));
        i.setFollowUp(row.getString("follow_up"));
        i.setFollowUpDate(row.getStringOrNull("follow_up_date"));
        i.setFollowUpDone(row.getBoolean("follow_up_done"));
        i.setCreatedAt(row.getString("created_at"));
        i.setUpdatedAt(row.getString("updated_at"));
        i.setChildName(row.getString("child_name").trim());
        i.setPhotoCount(row.getInt("photo_count"));
        return i;
    }

    // ------------------------------------------------------------------- queries

    public List<Interview> listForChild(long childId, LocalDate from, LocalDate to, String type) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE i.child_id = ?");
        params.add(childId);
        appendFilters(sql, params, null, from, to, type);
        sql.append(" ORDER BY i.date DESC, i.id DESC");
        return db.list(sql.toString(), InterviewDao::map, params.toArray());
    }

    public List<Interview> listForGroup(long groupId, LocalDate from, LocalDate to, String type) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        appendFilters(sql, params, groupId, from, to, type);
        sql.append(" ORDER BY i.date DESC, i.id DESC");
        return db.list(sql.toString(), InterviewDao::map, params.toArray());
    }

    public List<Interview> listForChildren(List<Child> children, LocalDate from, LocalDate to,
                                           String type, int limit) {
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> params = new ArrayList<>();
        String placeholders = String.join(",", children.stream().map(c -> "?").toList());
        StringBuilder sql = new StringBuilder(SELECT_BASE)
                .append("WHERE i.child_id IN (").append(placeholders).append(")");
        params.addAll(children.stream().map(Child::getId).toList());
        appendFilters(sql, params, null, from, to, type);
        sql.append(" ORDER BY i.date DESC, i.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return db.list(sql.toString(), InterviewDao::map, params.toArray());
    }

    /** Interviews anywhere, newest first (dashboard and search). */
    public List<Interview> listRecent(int limit) {
        return db.list(SELECT_BASE + " ORDER BY i.date DESC, i.id DESC LIMIT " + Math.max(1, limit),
                InterviewDao::map);
    }

    public List<Interview> search(String keyword, Long childId, Long groupId,
                                  LocalDate from, LocalDate to, String type, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (!Text.isBlank(keyword)) {
            sql.append(" AND (LOWER(i.topics) LIKE ? OR LOWER(i.discussed) LIKE ?"
                    + " OR LOWER(i.conclusions) LIKE ? OR LOWER(i.follow_up) LIKE ?"
                    + " OR LOWER(i.participants) LIKE ?)");
            String like = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if (childId != null && childId > 0) {
            sql.append(" AND i.child_id = ?");
            params.add(childId);
        }
        appendFilters(sql, params, groupId, from, to, type);
        sql.append(" ORDER BY i.date DESC, i.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return db.list(sql.toString(), InterviewDao::map, params.toArray());
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, Long groupId,
                                      LocalDate from, LocalDate to, String type) {
        if (groupId != null && groupId > 0) {
            sql.append(" AND i.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND i.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND i.date <= ?");
            params.add(Dates.toIso(to));
        }
        if (!Text.isBlank(type) && !"all".equalsIgnoreCase(type)) {
            sql.append(" AND i.type = ?");
            params.add(type);
        }
    }

    /**
     * Follow-ups that are still outstanding.
     *
     * <p>Only those with a due date that has arrived or is within {@code withinDays}
     * are returned, so the dashboard can nudge about genuinely pressing items.
     */
    public List<Interview> listDueFollowUps(int withinDays, int limit) {
        String cutoff = Dates.toIso(LocalDate.now().plusDays(Math.max(0, withinDays)));
        return db.list(SELECT_BASE
                + " WHERE i.follow_up_done = 0 AND TRIM(i.follow_up) <> ''"
                + "   AND i.follow_up_date IS NOT NULL AND i.follow_up_date <= ?"
                + " ORDER BY i.follow_up_date ASC, i.id DESC LIMIT " + Math.max(1, limit),
                InterviewDao::map, cutoff);
    }

    public Interview find(long id) {
        return db.one(SELECT_BASE + " WHERE i.id = ?", InterviewDao::map, id);
    }

    public int countForChild(long childId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS n FROM interviews WHERE child_id = ?");
        params.add(childId);
        if (from != null) {
            sql.append(" AND date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND date <= ?");
            params.add(Dates.toIso(to));
        }
        return (int) db.count(sql.toString(), params.toArray());
    }

    // -------------------------------------------------------------------- writes

    public long insert(Interview i) {
        String now = Db.now();
        long id = db.insert("""
                INSERT INTO interviews(child_id, group_id, date, type, mode, participants, topics,
                                       discussed, conclusions, follow_up, follow_up_date,
                                       follow_up_done, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                i.getChildId(), i.getGroupId() == 0 ? null : i.getGroupId(), i.getDate(), i.getType(),
                i.getMode(), i.getParticipants(), i.getTopics(), i.getDiscussed(), i.getConclusions(),
                i.getFollowUp(), i.getFollowUpDate(), i.isFollowUpDone(), now, now);
        i.setId(id);
        i.setCreatedAt(now);
        i.setUpdatedAt(now);
        return id;
    }

    public void update(Interview i) {
        String now = Db.now();
        db.update("""
                UPDATE interviews SET child_id = ?, group_id = ?, date = ?, type = ?, mode = ?,
                                      participants = ?, topics = ?, discussed = ?, conclusions = ?,
                                      follow_up = ?, follow_up_date = ?, follow_up_done = ?, updated_at = ?
                WHERE id = ?""",
                i.getChildId(), i.getGroupId() == 0 ? null : i.getGroupId(), i.getDate(), i.getType(),
                i.getMode(), i.getParticipants(), i.getTopics(), i.getDiscussed(), i.getConclusions(),
                i.getFollowUp(), i.getFollowUpDate(), i.isFollowUpDone(), now, i.getId());
        i.setUpdatedAt(now);
    }

    public void save(Interview i) {
        if (i.getId() == 0) {
            insert(i);
        } else {
            update(i);
        }
    }

    public void setFollowUpDone(long id, boolean done) {
        db.run(() -> db.update("UPDATE interviews SET follow_up_done = ?, updated_at = ? WHERE id = ?",
                done, Db.now(), id));
    }

    public void delete(long id) {
        db.run(() -> db.update("DELETE FROM interviews WHERE id = ?", id));
    }

    // ------------------------------------------------------------------- history

    public List<HistoryEntry> historyForChild(long childId, LocalDate from, LocalDate to, String type) {
        List<HistoryEntry> out = new ArrayList<>();
        for (Interview i : listForChild(childId, from, to, type)) {
            String detail = buildDetail(i);
            out.add(HistoryEntry.interview(i.getDate(), i.getId(),
                    Text.joinNonBlank(" \u00b7 ", i.typeEnum().label(), i.modeEnum().label()),
                    i.headline(), detail, i.getPhotoCount()));
        }
        return out;
    }

    private static String buildDetail(Interview i) {
        StringBuilder sb = new StringBuilder();
        append(sb, "Participants", i.getParticipants());
        append(sb, "Topics", i.getTopics());
        append(sb, "Discussed", i.getDiscussed());
        append(sb, "Conclusions", i.getConclusions());
        if (!Text.isBlank(i.getFollowUp())) {
            String due = Text.isBlank(i.getFollowUpDate())
                    ? ""
                    : " (due " + Dates.display(i.getFollowUpDate()) + ")";
            append(sb, "Follow-up", i.getFollowUp() + due);
        }
        return sb.toString().trim();
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (Text.isBlank(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(label).append(": ").append(value.trim());
    }
}
