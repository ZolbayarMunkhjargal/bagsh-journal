package app.kinderjournal.model;

import app.kinderjournal.util.Text;

import java.time.LocalDateTime;

/** One child's attendance on one day. There is at most one row per child per date. */
public class Attendance {

    private long id;
    private long childId;
    private long groupId;          // the group the child was in on that date
    private String date = "";      // ISO yyyy-MM-dd
    private String status = Choices.AttendanceStatus.PRESENT.id();
    private String reason = "";    // e.g. why the child was absent
    private String note = "";
    private String arrivalTime = "";
    private String departureTime = "";
    private String recordedAt = "";

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getChildId() {
        return childId;
    }

    public void setChildId(long childId) {
        this.childId = childId;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date == null ? "" : date;
    }

    public LocalDateTime dateTime() {
        return app.kinderjournal.util.Dates.fromIso(date) == null
                ? null
                : app.kinderjournal.util.Dates.fromIso(date).atStartOfDay();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? Choices.AttendanceStatus.PRESENT.id() : status;
    }

    public Choices.AttendanceStatus statusEnum() {
        return Choices.AttendanceStatus.fromId(status);
    }

    public void setStatusEnum(Choices.AttendanceStatus value) {
        this.status = value.id();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null ? "" : note;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime == null ? "" : arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime == null ? "" : departureTime;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt == null ? "" : recordedAt;
    }

    /** "Present" / "Absent \u2013 unwell" style label for lists and reports. */
    public String label() {
        String base = statusEnum().label();
        String extra = Text.joinNonBlank(" \u2013 ", reason, note);
        return extra.isEmpty() ? base : base + " (" + extra + ")";
    }
}
