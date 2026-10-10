package app.kinderjournal.model;

/** A kindergarten class or group. Acts as the container for children and records. */
public class Group {

    private long id;
    private String name = "";
    private String description = "";
    private String ageRange = "";
    private String room = "";
    private String teacher = "";
    private String schoolYear = "";
    private boolean archived;
    private int sortOrder;
    private String createdAt = "";
    private String updatedAt = "";

    /** Number of active children, filled in by list queries (not stored). */
    private int childCount = -1;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getAgeRange() {
        return ageRange;
    }

    public void setAgeRange(String ageRange) {
        this.ageRange = ageRange == null ? "" : ageRange;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room == null ? "" : room;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher == null ? "" : teacher;
    }

    public String getSchoolYear() {
        return schoolYear;
    }

    public void setSchoolYear(String schoolYear) {
        this.schoolYear = schoolYear == null ? "" : schoolYear;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }

    /** A short "Ages 4-5 · Sunflower room" style subtitle; may be empty. */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (!ageRange.isBlank()) {
            sb.append(app.kinderjournal.i18n.Messages.get("group.ageRange", ageRange));
        }
        if (!room.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" \u00b7 ");
            }
            sb.append(room);
        }
        if (!schoolYear.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" \u00b7 ");
            }
            sb.append(schoolYear);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name;
    }
}
