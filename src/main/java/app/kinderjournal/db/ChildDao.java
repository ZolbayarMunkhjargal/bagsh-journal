package app.kinderjournal.db;

import app.kinderjournal.model.Child;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes child profiles. */
public class ChildDao {

    private static final String SELECT_BASE = """
            SELECT c.id, c.first_name, c.last_name, c.birth_date, c.gender, c.group_id,
                   c.start_date, c.guardian_name, c.guardian_relation, c.guardian_phone,
                   c.guardian_email, c.second_contact, c.address, c.allergies, c.notes,
                   c.photo_file, c.archived, c.created_at, c.updated_at,
                   COALESCE(g.name, '') AS group_name
            FROM children c
            LEFT JOIN groups g ON g.id = c.group_id
            """;

    private static final String ORDER = " ORDER BY c.last_name COLLATE NOCASE ASC, "
            + "c.first_name COLLATE NOCASE ASC, c.id ASC";

    private final Db db;

    public ChildDao(Db db) {
        this.db = db;
    }

    private static Child map(Row row) {
        Child c = new Child();
        c.setId(row.getLong("id"));
        c.setFirstName(row.getString("first_name"));
        c.setLastName(row.getString("last_name"));
        c.setBirthDate(row.getStringOrNull("birth_date"));
        c.setGender(row.getString("gender"));
        c.setGroupId(row.getLong("group_id"));
        c.setStartDate(row.getStringOrNull("start_date"));
        c.setGuardianName(row.getString("guardian_name"));
        c.setGuardianRelation(row.getString("guardian_relation"));
        c.setGuardianPhone(row.getString("guardian_phone"));
        c.setGuardianEmail(row.getString("guardian_email"));
        c.setSecondContact(row.getString("second_contact"));
        c.setAddress(row.getString("address"));
        c.setAllergies(row.getString("allergies"));
        c.setNotes(row.getString("notes"));
        c.setPhotoFile(row.getStringOrNull("photo_file"));
        c.setArchived(row.getBoolean("archived"));
        c.setCreatedAt(row.getString("created_at"));
        c.setUpdatedAt(row.getString("updated_at"));
        c.setGroupName(row.getString("group_name"));
        return c;
    }

    /** Children of one group. Pass 0 for "every group" and -1 for "not assigned". */
    public List<Child> listByGroup(long groupId, boolean includeArchived) {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (groupId > 0) {
            where.add("c.group_id = ?");
            params.add(groupId);
        } else if (groupId == -1) {
            where.add("c.group_id IS NULL");
        }
        if (!includeArchived) {
            where.add("c.archived = 0");
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        sql.append(ORDER);
        return db.list(sql.toString(), ChildDao::map, params.toArray());
    }

    public List<Child> listAll() {
        return listByGroup(0, true);
    }

    public List<Child> listActive() {
        return listByGroup(0, false);
    }

    public Child find(long id) {
        return db.one(SELECT_BASE + " WHERE c.id = ?", ChildDao::map, id);
    }

    /** Case-insensitive name search used by the advanced search screen. */
    public List<Child> searchByName(String text) {
        String like = "%" + (text == null ? "" : text.trim().toLowerCase()) + "%";
        return db.list(SELECT_BASE
                + " WHERE LOWER(c.first_name || ' ' || c.last_name) LIKE ?"
                + "    OR LOWER(c.last_name || ' ' || c.first_name) LIKE ?" + ORDER,
                ChildDao::map, like, like);
    }

    /** Children whose name matches a text that may be "first last" in either order. */
    public List<Child> listForGroupFilter(long groupId) {
        return listByGroup(groupId, true);
    }

    public long insert(Child c) {
        String now = Db.now();
        long id = db.insert("""
                INSERT INTO children(first_name, last_name, birth_date, gender, group_id, start_date,
                                     guardian_name, guardian_relation, guardian_phone, guardian_email,
                                     second_contact, address, allergies, notes, photo_file, archived,
                                     created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                c.getFirstName(), c.getLastName(), c.getBirthDate(), c.getGender(),
                c.getGroupId() == 0 ? null : c.getGroupId(), c.getStartDate(),
                c.getGuardianName(), c.getGuardianRelation(), c.getGuardianPhone(),
                c.getGuardianEmail(), c.getSecondContact(), c.getAddress(), c.getAllergies(),
                c.getNotes(), c.getPhotoFile(), c.isArchived(), now, now);
        c.setId(id);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return id;
    }

    public void update(Child c) {
        String now = Db.now();
        db.update("""
                UPDATE children SET first_name = ?, last_name = ?, birth_date = ?, gender = ?,
                                    group_id = ?, start_date = ?, guardian_name = ?,
                                    guardian_relation = ?, guardian_phone = ?, guardian_email = ?,
                                    second_contact = ?, address = ?, allergies = ?, notes = ?,
                                    photo_file = ?, archived = ?, updated_at = ?
                WHERE id = ?""",
                c.getFirstName(), c.getLastName(), c.getBirthDate(), c.getGender(),
                c.getGroupId() == 0 ? null : c.getGroupId(), c.getStartDate(),
                c.getGuardianName(), c.getGuardianRelation(), c.getGuardianPhone(),
                c.getGuardianEmail(), c.getSecondContact(), c.getAddress(), c.getAllergies(),
                c.getNotes(), c.getPhotoFile(), c.isArchived(), now, c.getId());
        c.setUpdatedAt(now);
    }

    public void save(Child c) {
        if (c.getId() == 0) {
            insert(c);
        } else {
            update(c);
        }
    }

    /**
     * Moves a child to another class.
     *
     * <p>Only the profile changes: attendance, observations and interviews keep
     * the group they were recorded under, so a child's history stays intact and
     * past class registers still show who was actually there.
     */
    public void moveToGroup(long childId, long groupId) {
        db.run(() -> db.update("UPDATE children SET group_id = ?, updated_at = ? WHERE id = ?",
                groupId == 0 ? null : groupId, Db.now(), childId));
    }

    public void setPhotoFile(long childId, String photoFile) {
        db.run(() -> db.update("UPDATE children SET photo_file = ?, updated_at = ? WHERE id = ?",
                photoFile, Db.now(), childId));
    }

    public void setArchived(long childId, boolean archived) {
        db.run(() -> db.update("UPDATE children SET archived = ?, updated_at = ? WHERE id = ?",
                archived, Db.now(), childId));
    }

    /**
     * Deletes a child and everything attached to them.
     *
     * <p>Attendance, observations, interviews and photo rows go with them through
     * {@code ON DELETE CASCADE}; the caller is responsible for deleting the image
     * files from the photos folder.
     */
    public void delete(long childId) {
        db.run(() -> db.update("DELETE FROM children WHERE id = ?", childId));
    }

    /** The group id stored on the profile, or 0 when unassigned. */
    public long groupIdOf(long childId) {
        Child c = find(childId);
        return c == null ? 0 : c.getGroupId();
    }

    /** How many children belong to a group (used by the class list refresh). */
    public int countInGroup(long groupId) {
        return (int) db.count("SELECT COUNT(*) AS n FROM children WHERE group_id = ? AND archived = 0",
                groupId);
    }
}
