package app.kinderjournal.db;

import app.kinderjournal.model.Attendance;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes daily attendance. */
public class AttendanceDao {

    /**
     * Totals for a period.
     *
     * @param present  days marked present
     * @param absent   days marked absent
     * @param late     days marked late
     * @param excused  days marked excused
     * @param recorded total number of records found
     */
    public record Summary(int present, int absent, int late, int excused, int recorded) {

        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0);

        public Summary plus(Summary other) {
            return new Summary(present + other.present, absent + other.absent, late + other.late,
                    excused + other.excused, recorded + other.recorded);
        }

        public int attending() {
            return present + late;
        }

        /** Attendance rate as a whole percentage, or -1 when nothing was recorded. */
        public int ratePercent() {
            if (recorded == 0) {
                return -1;
            }
            return (int) Math.round(attending() * 100.0 / recorded);
        }

        public String rateText() {
            int rate = ratePercent();
            return rate < 0 ? "\u2013" : rate + "%";
        }
    }

    private static final String SELECT_BASE = """
            SELECT a.id, a.child_id, a.group_id, a.date, a.status, a.reason, a.note,
                   a.arrival_time, a.departure_time, a.recorded_at
            FROM attendance a
            """;

    private final Db db;

    public AttendanceDao(Db db) {
        this.db = db;
    }

    private static Attendance map(Row row) {
        Attendance a = new Attendance();
        a.setId(row.getLong("id"));
        a.setChildId(row.getLong("child_id"));
        a.setGroupId(row.getLong("group_id"));
        a.setDate(row.getString("date"));
        a.setStatus(row.getString("status"));
        a.setReason(row.getString("reason"));
        a.setNote(row.getString("note"));
        a.setArrivalTime(row.getString("arrival_time"));
        a.setDepartureTime(row.getString("departure_time"));
        a.setRecordedAt(row.getString("recorded_at"));
        return a;
    }

    // ------------------------------------------------------------------- queries

    /** Attendance rows for one group on one date, keyed by child id. */
    public Map<Long, Attendance> mapForGroupOnDate(long groupId, LocalDate date) {
        List<Attendance> rows = db.list(SELECT_BASE
                + "WHERE a.group_id = ? AND a.date = ?", AttendanceDao::map, groupId, Dates.toIso(date));
        Map<Long, Attendance> out = new LinkedHashMap<>();
        for (Attendance a : rows) {
            out.put(a.getChildId(), a);
        }
        return out;
    }

    /** Attendance rows for a set of children on one date, keyed by child id. */
    public Map<Long, Attendance> mapForChildrenOnDate(List<Long> childIds, LocalDate date) {
        Map<Long, Attendance> out = new LinkedHashMap<>();
        if (childIds == null || childIds.isEmpty()) {
            return out;
        }
        String iso = Dates.toIso(date);
        for (Long childId : childIds) {
            Attendance a = find(childId, iso);
            if (a != null) {
                out.put(childId, a);
            }
        }
        return out;
    }

    /** Every record for one date, across all groups. */
    public List<Attendance> listByDate(LocalDate date) {
        return db.list(SELECT_BASE + "WHERE a.date = ?", AttendanceDao::map, Dates.toIso(date));
    }

    /** Records for a set of children between two dates, newest first. */
    public List<Attendance> listForChildren(List<Long> childIds, LocalDate from, LocalDate to) {
        if (childIds == null || childIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", childIds.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>(childIds);
        StringBuilder sql = new StringBuilder(SELECT_BASE)
                .append("WHERE a.child_id IN (").append(placeholders).append(")");
        if (from != null) {
            sql.append(" AND a.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND a.date <= ?");
            params.add(Dates.toIso(to));
        }
        sql.append(" ORDER BY a.date DESC, a.child_id ASC");
        return db.list(sql.toString(), AttendanceDao::map, params.toArray());
    }

    /** One child's attendance history, newest first. */
    public List<Attendance> listForChild(long childId, LocalDate from, LocalDate to) {
        return listForChildren(List.of(childId), from, to);
    }

    /** Group records in a date range (used by reports and summaries). */
    public List<Attendance> listForGroup(long groupId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1");
        if (groupId > 0) {
            sql.append(" AND a.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND a.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND a.date <= ?");
            params.add(Dates.toIso(to));
        }
        sql.append(" ORDER BY a.date ASC, a.child_id ASC");
        return db.list(sql.toString(), AttendanceDao::map, params.toArray());
    }

    /** The single record for a child on a date, or null. */
    public Attendance find(long childId, String isoDate) {
        return db.one(SELECT_BASE + "WHERE a.child_id = ? AND a.date = ?",
                AttendanceDao::map, childId, isoDate);
    }

    /** Totals for one child over a period. */
    public Summary summaryForChild(long childId, LocalDate from, LocalDate to) {
        return summarise(listForChild(childId, from, to));
    }

    /** Totals for a group over a period. */
    public Summary summaryForGroup(long groupId, LocalDate from, LocalDate to) {
        return summarise(listForGroup(groupId, from, to));
    }

    /** Totals per child for a set of children, keyed by child id (missing = none recorded). */
    public Map<Long, Summary> summariesByChild(List<Child> children, LocalDate from, LocalDate to) {
        List<Long> ids = new ArrayList<>();
        for (Child c : children) {
            ids.add(c.getId());
        }
        Map<Long, Summary> out = new LinkedHashMap<>();
        for (Long id : ids) {
            out.put(id, Summary.EMPTY);
        }
        for (Attendance a : listForChildren(ids, from, to)) {
            Summary current = out.getOrDefault(a.getChildId(), Summary.EMPTY);
            out.put(a.getChildId(), current.plus(one(a.statusEnum())));
        }
        return out;
    }

    /** Totals per day for a group, keyed by ISO date (missing = nothing recorded). */
    public Map<String, Summary> summariesByDate(long groupId, LocalDate from, LocalDate to) {
        Map<String, Summary> out = new LinkedHashMap<>();
        for (Attendance a : listForGroup(groupId, from, to)) {
            Summary current = out.getOrDefault(a.getDate(), Summary.EMPTY);
            out.put(a.getDate(), current.plus(one(a.statusEnum())));
        }
        return out;
    }

    private static Summary summarise(List<Attendance> rows) {
        Summary total = Summary.EMPTY;
        for (Attendance a : rows) {
            total = total.plus(one(a.statusEnum()));
        }
        return total;
    }

    private static Summary one(Choices.AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> new Summary(1, 0, 0, 0, 1);
            case ABSENT -> new Summary(0, 1, 0, 0, 1);
            case LATE -> new Summary(0, 0, 1, 0, 1);
            case EXCUSED -> new Summary(0, 0, 0, 1, 1);
        };
    }

    /** The dates on which anything at all was recorded for a group, newest first. */
    public List<String> recordedDates(long groupId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT a.date FROM attendance a WHERE 1 = 1");
        if (groupId > 0) {
            sql.append(" AND a.group_id = ?");
            params.add(groupId);
        }
        if (from != null) {
            sql.append(" AND a.date >= ?");
            params.add(Dates.toIso(from));
        }
        if (to != null) {
            sql.append(" AND a.date <= ?");
            params.add(Dates.toIso(to));
        }
        sql.append(" ORDER BY a.date DESC");
        return db.list(sql.toString(), row -> row.getString("date"), params.toArray());
    }

    /** The most recent date with any attendance recorded for a group, or null. */
    public String mostRecentDate(long groupId) {
        List<String> dates = recordedDates(groupId, null, null);
        return dates.isEmpty() ? null : dates.get(0);
    }

    // -------------------------------------------------------------------- writes

    /**
     * Records attendance, replacing whatever was there for that child and date.
     *
     * <p>The unique index on (child_id, date) plus this upsert is what allows a
     * teacher to correct a mistake by simply marking the child again.
     */
    public void save(Attendance a) {
        db.transaction(() -> {
            db.update("""
                    INSERT INTO attendance(child_id, group_id, date, status, reason, note,
                                           arrival_time, departure_time, recorded_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(child_id, date) DO UPDATE SET
                        status = excluded.status,
                        group_id = excluded.group_id,
                        reason = excluded.reason,
                        note = excluded.note,
                        arrival_time = excluded.arrival_time,
                        departure_time = excluded.departure_time,
                        recorded_at = excluded.recorded_at""",
                    a.getChildId(), a.getGroupId() == 0 ? null : a.getGroupId(), a.getDate(),
                    a.getStatus(), a.getReason(), a.getNote(), a.getArrivalTime(),
                    a.getDepartureTime(), Db.now());
            return null;
        });
    }

    /** Saves a whole register in one transaction. */
    public void saveAll(List<Attendance> rows) {
        db.transaction(() -> {
            for (Attendance a : rows) {
                save(a);
            }
            return null;
        });
    }

    public void delete(long id) {
        db.run(() -> db.update("DELETE FROM attendance WHERE id = ?", id));
    }

    public void delete(long childId, String isoDate) {
        db.run(() -> db.update("DELETE FROM attendance WHERE child_id = ? AND date = ?", childId, isoDate));
    }

    /** Removes every record for a group on a date (used when clearing a day). */
    public void clearDay(long groupId, LocalDate date) {
        db.run(() -> db.update("DELETE FROM attendance WHERE group_id = ? AND date = ?",
                groupId, Dates.toIso(date)));
    }

    // ------------------------------------------------------------------- history

    /**
     * Attendance history for one child as timeline entries.
     *
     * <p>Days with no record are skipped rather than shown as absent, because a
     * blank day means "not recorded", not "not there".
     */
    public List<HistoryEntry> historyForChild(long childId, LocalDate from, LocalDate to) {
        List<HistoryEntry> out = new ArrayList<>();
        for (Attendance a : listForChild(childId, from, to)) {
            Choices.AttendanceStatus status = a.statusEnum();
            String summary = status.label();
            String detail = Text.joinNonBlank(" \u00b7 ", a.getReason(), a.getNote());
            if (!a.getArrivalTime().isBlank() || !a.getDepartureTime().isBlank()) {
                String times = Text.joinNonBlank(" \u2013 ", a.getArrivalTime(), a.getDepartureTime());
                detail = Text.joinNonBlank(" \u00b7 ", detail, times);
            }
            out.add(HistoryEntry.attendance(a.getDate(), a.getId(),
                    Dates.displayLong(a.getDate()), summary + (detail.isEmpty() ? "" : " \u2014 " + detail)));
        }
        return out;
    }
}
