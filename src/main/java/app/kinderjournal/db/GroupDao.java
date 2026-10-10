package app.kinderjournal.db;

import app.kinderjournal.model.Group;

import java.util.List;

/** Reads and writes classes/groups. */
public class GroupDao {

    private static final String COLUMNS =
            "id, name, description, age_range, room, teacher, school_year, archived, "
            + "sort_order, created_at, updated_at";

    /** Adds the live child count to every group query. */
    private static final String SELECT_BASE =
            "SELECT g." + COLUMNS.replace(", ", ", g.") + ", "
            + "(SELECT COUNT(*) FROM children c WHERE c.group_id = g.id AND c.archived = 0) AS child_count "
            + "FROM groups g ";

    private final Db db;

    public GroupDao(Db db) {
        this.db = db;
    }

    private static Group map(Row row) {
        Group g = new Group();
        g.setId(row.getLong("id"));
        g.setName(row.getString("name"));
        g.setDescription(row.getString("description"));
        g.setAgeRange(row.getString("age_range"));
        g.setRoom(row.getString("room"));
        g.setTeacher(row.getString("teacher"));
        g.setSchoolYear(row.getString("school_year"));
        g.setArchived(row.getBoolean("archived"));
        g.setSortOrder(row.getInt("sort_order"));
        g.setCreatedAt(row.getString("created_at"));
        g.setUpdatedAt(row.getString("updated_at"));
        g.setChildCount(row.getInt("child_count"));
        return g;
    }

    /** Every group, live ones first, each with its child count. */
    public List<Group> listAll() {
        return db.list(SELECT_BASE + "ORDER BY g.archived ASC, g.sort_order ASC, "
                + "g.name COLLATE NOCASE ASC", GroupDao::map);
    }

    /** Only the groups still in use. */
    public List<Group> listActive() {
        return db.list(SELECT_BASE + "WHERE g.archived = 0 "
                + "ORDER BY g.sort_order ASC, g.name COLLATE NOCASE ASC", GroupDao::map);
    }

    public Group find(long id) {
        return db.one(SELECT_BASE + "WHERE g.id = ?", GroupDao::map, id);
    }

    public Group findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return db.one(SELECT_BASE + "WHERE g.name = ? COLLATE NOCASE", GroupDao::map, name.trim());
    }

    /** The group a brand-new installation should start on, or null when none exist. */
    public Group firstActive() {
        List<Group> groups = listActive();
        return groups.isEmpty() ? null : groups.get(0);
    }

    public long insert(Group g) {
        String now = Db.now();
        long id = db.insert("""
                INSERT INTO groups(name, description, age_range, room, teacher, school_year,
                                   archived, sort_order, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                g.getName(), g.getDescription(), g.getAgeRange(), g.getRoom(), g.getTeacher(),
                g.getSchoolYear(), g.isArchived(), g.getSortOrder(), now, now);
        g.setId(id);
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        return id;
    }

    public void update(Group g) {
        String now = Db.now();
        db.update("""
                UPDATE groups SET name = ?, description = ?, age_range = ?, room = ?, teacher = ?,
                                  school_year = ?, archived = ?, sort_order = ?, updated_at = ?
                WHERE id = ?""",
                g.getName(), g.getDescription(), g.getAgeRange(), g.getRoom(), g.getTeacher(),
                g.getSchoolYear(), g.isArchived(), g.getSortOrder(), now, g.getId());
        g.setUpdatedAt(now);
    }

    public void save(Group g) {
        if (g.getId() == 0) {
            insert(g);
        } else {
            update(g);
        }
    }

    /** Reorders groups to match the given list of ids. */
    public void reorder(List<Long> orderedIds) {
        db.transaction(() -> {
            for (int i = 0; i < orderedIds.size(); i++) {
                db.update("UPDATE groups SET sort_order = ? WHERE id = ?", i, orderedIds.get(i));
            }
            return null;
        });
    }

    /** True when the group still has children pointing at it. */
    public int childCount(long groupId) {
        return (int) db.count("SELECT COUNT(*) AS n FROM children WHERE group_id = ?", groupId);
    }

    /**
     * Deletes a group.
     *
     * <p>Children are not deleted with it: their {@code group_id} becomes NULL so
     * that a class can be removed without destroying a child's history. Refuses
     * when the group still has children, so the teacher has to move them first.
     */
    public boolean delete(long groupId) {
        if (childCount(groupId) > 0) {
            return false;
        }
        db.transaction(() -> {
            db.update("UPDATE lesson_plans SET group_id = NULL WHERE group_id = ?", groupId);
            db.update("DELETE FROM groups WHERE id = ?", groupId);
            return null;
        });
        return true;
    }

    /** Moves every child of one group to another (used when merging classes). */
    public void moveChildren(long fromGroupId, long toGroupId) {
        db.run(() -> db.update("UPDATE children SET group_id = ? WHERE group_id = ?",
                toGroupId == 0 ? null : toGroupId, fromGroupId));
    }
}
