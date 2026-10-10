package app.kinderjournal.db;

import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.LessonPlan;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes lesson and activity plans. */
public class PlanDao {

    private static final String SELECT_BASE = """
            SELECT p.id, p.group_id, p.title, p.date, p.end_date, p.start_time, p.end_time, p.area,
                   p.objectives, p.description, p.materials, p.sequence, p.instructions, p.status,
                   p.reflection, p.template, p.recurring, p.sort_order, p.created_at, p.updated_at,
                   COALESCE(g.name, '') AS group_name
            FROM lesson_plans p
            LEFT JOIN groups g ON g.id = p.group_id
            """;

    private static final String ORDER = " ORDER BY p.date ASC, "
            + "CASE WHEN p.start_time = '' THEN 1 ELSE 0 END ASC, p.start_time ASC, p.sort_order ASC, p.id ASC";

    private final Db db;

    public PlanDao(Db db) {
        this.db = db;
    }

    private static LessonPlan map(Row row) {
        LessonPlan p = new LessonPlan();
        p.setId(row.getLong("id"));
        p.setGroupId(row.getLong("group_id"));
        p.setTitle(row.getString("title"));
        p.setDate(row.getString("date"));
        p.setEndDate(row.getStringOrNull("end_date"));
        p.setStartTime(row.getString("start_time"));
        p.setEndTime(row.getString("end_time"));
        p.setArea(row.getString("area"));
        p.setObjectives(row.getString("objectives"));
        p.setDescription(row.getString("description"));
        p.setMaterials(row.getString("materials"));
        p.setSequence(row.getString("sequence"));
        p.setInstructions(row.getString("instructions"));
        p.setStatus(row.getString("status"));
        p.setReflection(row.getString("reflection"));
        p.setTemplate(row.getBoolean("template"));
        p.setRecurring(row.getBoolean("recurring"));
        p.setSortOrder(row.getInt("sort_order"));
        p.setCreatedAt(row.getString("created_at"));
        p.setUpdatedAt(row.getString("updated_at"));
        p.setGroupName(row.getString("group_name"));
        return p;
    }

    // ------------------------------------------------------------------- queries

    /** Everything scheduled on one day for a group, including multi-day plans covering it. */
    public List<LessonPlan> listForDay(long groupId, LocalDate day) {
        String iso = Dates.toIso(day);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE)
                .append("WHERE p.template = 0 AND p.date <= ?")
                .append(" AND COALESCE(p.end_date, p.date) >= ?");
        params.add(iso);
        params.add(iso);
        if (groupId > 0) {
            sql.append(" AND (p.group_id = ? OR p.group_id IS NULL)");
            params.add(groupId);
        }
        sql.append(ORDER);
        return db.list(sql.toString(), PlanDao::map, params.toArray());
    }

    /** Plans whose start date falls in a range (used by the weekly view). */
    public List<LessonPlan> listBetween(long groupId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE p.template = 0");
        if (from != null) {
            sql.append(" AND COALESCE(p.end_date, p.date) >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND p.date <= ?");
            params.add(Dates.toIso(to));
        }
        if (groupId > 0) {
            sql.append(" AND (p.group_id = ? OR p.group_id IS NULL)");
            params.add(groupId);
        }
        sql.append(ORDER);
        return db.list(sql.toString(), PlanDao::map, params.toArray());
    }

    /** Plans for a group grouped by ISO date, for laying out a week. */
    public Map<String, List<LessonPlan>> byDay(long groupId, LocalDate from, LocalDate to) {
        Map<String, List<LessonPlan>> out = new LinkedHashMap<>();
        for (LessonPlan p : listBetween(groupId, from, to)) {
            // A multi-day plan appears on each day it covers.
            LocalDate start = p.localDate();
            LocalDate end = Dates.fromIso(p.effectiveEndDate());
            if (start == null) {
                continue;
            }
            if (end == null || end.isBefore(start)) {
                end = start;
            }
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                out.computeIfAbsent(Dates.toIso(d), k -> new ArrayList<>()).add(p);
            }
        }
        return out;
    }

    /** Upcoming plans from a date onwards (dashboard). */
    public List<LessonPlan> listUpcoming(long groupId, LocalDate from, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE)
                .append("WHERE p.template = 0 AND COALESCE(p.end_date, p.date) >= ?");
        params.add(Dates.toIso(from));
        if (groupId > 0) {
            sql.append(" AND (p.group_id = ? OR p.group_id IS NULL)");
            params.add(groupId);
        }
        sql.append(ORDER).append(" LIMIT ").append(Math.max(1, limit));
        return db.list(sql.toString(), PlanDao::map, params.toArray());
    }

    /** Templates a teacher can reuse. */
    public List<LessonPlan> listTemplates() {
        return db.list(SELECT_BASE + "WHERE p.template = 1 ORDER BY p.title COLLATE NOCASE ASC",
                PlanDao::map);
    }

    public List<LessonPlan> listAll(long groupId, LocalDate from, LocalDate to, String status, String area) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (groupId > 0) {
            sql.append(" AND p.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND COALESCE(p.end_date, p.date) >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND p.date <= ?");
            params.add(Dates.toIso(to));
        }
        if (!Text.isBlank(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        if (!Text.isBlank(area) && !"all".equalsIgnoreCase(area)) {
            sql.append(" AND p.area = ?");
            params.add(area);
        }
        sql.append(ORDER);
        return db.list(sql.toString(), PlanDao::map, params.toArray());
    }

    /** Full-text search over the plan text, used by the advanced search screen. */
    public List<LessonPlan> search(String keyword, Long groupId, LocalDate from, LocalDate to,
                                   String status, String area, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (!Text.isBlank(keyword)) {
            sql.append(" AND (LOWER(p.title) LIKE ? OR LOWER(p.description) LIKE ?"
                    + " OR LOWER(p.objectives) LIKE ? OR LOWER(p.sequence) LIKE ?"
                    + " OR LOWER(p.materials) LIKE ? OR LOWER(p.reflection) LIKE ?)");
            String like = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 6; i++) {
                params.add(like);
            }
        }
        if (groupId != null && groupId > 0) {
            sql.append(" AND p.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND COALESCE(p.end_date, p.date) >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND p.date <= ?");
            params.add(Dates.toIso(to));
        }
        if (!Text.isBlank(status) && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        if (!Text.isBlank(area) && !"all".equalsIgnoreCase(area)) {
            sql.append(" AND p.area = ?");
            params.add(area);
        }
        sql.append(ORDER);
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return db.list(sql.toString(), PlanDao::map, params.toArray());
    }

    public LessonPlan find(long id) {
        return db.one(SELECT_BASE + "WHERE p.id = ?", PlanDao::map, id);
    }

    /** How many plans a group has on a given day (dashboard). */
    public int countForDay(long groupId, LocalDate day) {
        return listForDay(groupId, day).size();
    }

    public int countByStatus(long groupId, LocalDate from, LocalDate to, String status) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS n FROM lesson_plans p WHERE p.template = 0 AND p.status = ?");
        params.add(status);
        if (groupId > 0) {
            sql.append(" AND (p.group_id = ? OR p.group_id IS NULL)");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND COALESCE(p.end_date, p.date) >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND p.date <= ?");
            params.add(Dates.toIso(to));
        }
        return (int) db.count(sql.toString(), params.toArray());
    }

    // -------------------------------------------------------------------- writes

    public long insert(LessonPlan p) {
        String now = Db.now();
        long id = db.insert("""
                INSERT INTO lesson_plans(group_id, title, date, end_date, start_time, end_time, area,
                                         objectives, description, materials, sequence, instructions,
                                         status, reflection, template, recurring, sort_order,
                                         created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                p.getGroupId() == 0 ? null : p.getGroupId(), p.getTitle(), p.getDate(), p.getEndDate(),
                p.getStartTime(), p.getEndTime(), p.getArea(), p.getObjectives(), p.getDescription(),
                p.getMaterials(), p.getSequence(), p.getInstructions(), p.getStatus(), p.getReflection(),
                p.isTemplate(), p.isRecurring(), p.getSortOrder(), now, now);
        p.setId(id);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return id;
    }

    public void update(LessonPlan p) {
        String now = Db.now();
        db.update("""
                UPDATE lesson_plans SET group_id = ?, title = ?, date = ?, end_date = ?, start_time = ?,
                                        end_time = ?, area = ?, objectives = ?, description = ?,
                                        materials = ?, sequence = ?, instructions = ?, status = ?,
                                        reflection = ?, template = ?, recurring = ?, sort_order = ?,
                                        updated_at = ?
                WHERE id = ?""",
                p.getGroupId() == 0 ? null : p.getGroupId(), p.getTitle(), p.getDate(), p.getEndDate(),
                p.getStartTime(), p.getEndTime(), p.getArea(), p.getObjectives(), p.getDescription(),
                p.getMaterials(), p.getSequence(), p.getInstructions(), p.getStatus(), p.getReflection(),
                p.isTemplate(), p.isRecurring(), p.getSortOrder(), now, p.getId());
        p.setUpdatedAt(now);
    }

    public void save(LessonPlan p) {
        if (p.getId() == 0) {
            insert(p);
        } else {
            update(p);
        }
    }

    /** Marks a plan completed/postponed and stores the post-activity reflection. */
    public void setStatusAndReflection(long id, String status, String reflection) {
        db.run(() -> db.update(
                "UPDATE lesson_plans SET status = ?, reflection = ?, updated_at = ? WHERE id = ?",
                status, reflection == null ? "" : reflection, Db.now(), id));
    }

    /** Copies a plan (optionally onto a new date) and returns the new id. */
    public long duplicate(long sourceId, LocalDate newDate, boolean asTemplate) {
        LessonPlan source = find(sourceId);
        if (source == null) {
            throw new DataException("The plan to copy no longer exists");
        }
        LessonPlan copy = source.clone();
        copy.setId(0);
        if (newDate != null) {
            long span = 0;
            LocalDate start = source.localDate();
            LocalDate end = Dates.fromIso(source.effectiveEndDate());
            if (start != null && end != null) {
                span = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            }
            copy.setDate(Dates.toIso(newDate));
            copy.setEndDate(span > 0 ? Dates.toIso(newDate.plusDays(span)) : null);
        }
        copy.setTemplate(asTemplate);
        copy.setStatus(app.kinderjournal.model.Choices.PlanStatus.PLANNED.id());
        copy.setReflection("");
        return insert(copy);
    }

    /** Moves every plan in a range by a number of days (e.g. after a closure day). */
    public int shiftRange(long groupId, LocalDate from, LocalDate to, int days) {
        List<LessonPlan> plans = listBetween(groupId, from, to);
        db.transaction(() -> {
            for (LessonPlan p : plans) {
                LocalDate start = p.localDate();
                LocalDate end = Dates.fromIso(p.effectiveEndDate());
                if (start != null) {
                    p.setDate(Dates.toIso(start.plusDays(days)));
                }
                if (end != null && p.isRange()) {
                    p.setEndDate(Dates.toIso(end.plusDays(days)));
                }
                update(p);
            }
            return null;
        });
        return plans.size();
    }

    public void delete(long id) {
        db.run(() -> db.update("DELETE FROM lesson_plans WHERE id = ?", id));
    }

    // ------------------------------------------------------------------- history

    /**
     * Plans a child's class took part in.
     *
     * <p>A plan belongs to a whole class rather than to one child, so this is
     * reported as context in the child's timeline: "the group did this that day".
     */
    public List<HistoryEntry> historyForGroup(long groupId, LocalDate from, LocalDate to) {
        List<HistoryEntry> out = new ArrayList<>();
        for (LessonPlan p : listBetween(groupId, from, to)) {
            out.add(HistoryEntry.plan(p.getDate(), p.getId(), p.getTitle(),
                    p.metaLine(), p.getDescription()));
        }
        return out;
    }
}
