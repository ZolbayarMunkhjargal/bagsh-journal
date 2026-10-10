package app.kinderjournal.model;

import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;

/** A planned lesson or activity for a class, on one day or across a range. */
public class LessonPlan {

    private long id;
    private long groupId;                 // 0 = applies to all / no group
    private String title = "";
    private String date = "";             // ISO start date
    private String endDate;               // ISO end date for weekly plans, may equal date
    private String startTime = "";        // "09:30", free text but validated
    private String endTime = "";
    private String area = Choices.PlanArea.OTHER.id();
    private String objectives = "";
    private String description = "";
    private String materials = "";
    private String sequence = "";         // planned order of activities
    private String instructions = "";     // extra notes for the teacher
    private String status = Choices.PlanStatus.PLANNED.id();
    private String reflection = "";       // written after the activity
    private boolean template;             // reusable as a starting point
    private boolean recurring;            // part of the weekly routine
    private int sortOrder;
    private String createdAt = "";
    private String updatedAt = "";

    /** Filled in by list queries (not stored). */
    private String groupName = "";

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date == null ? "" : date;
    }

    public LocalDate localDate() {
        return Dates.fromIso(date);
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = Text.nullIfBlank(endDate);
    }

    /** The last day the plan covers (same as the start date for single-day plans). */
    public String effectiveEndDate() {
        return Text.isBlank(endDate) ? date : endDate;
    }

    /** True when the plan spans more than one day. */
    public boolean isRange() {
        return !Text.isBlank(endDate) && !endDate.equals(date);
    }

    /** "12 Mar 2026" or "12 - 16 Mar 2026". */
    public String dateRangeLabel() {
        if (Text.isBlank(date)) {
            return "";
        }
        if (!isRange()) {
            return Dates.display(date);
        }
        LocalDate start = Dates.fromIso(date);
        LocalDate end = Dates.fromIso(endDate);
        if (start == null || end == null) {
            return Dates.display(date) + " \u2013 " + Dates.display(endDate);
        }
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + " \u2013 " + Dates.display(endDate);
        }
        return Dates.display(date) + " \u2013 " + Dates.display(endDate);
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime == null ? "" : startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime == null ? "" : endTime;
    }

    /** "09:30 - 10:15", or whichever half is known. */
    public String timeLabel() {
        if (startTime.isBlank() && endTime.isBlank()) {
            return "";
        }
        if (endTime.isBlank()) {
            return startTime;
        }
        if (startTime.isBlank()) {
            return endTime;
        }
        return startTime + " \u2013 " + endTime;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area == null ? Choices.PlanArea.OTHER.id() : area;
    }

    public Choices.PlanArea areaEnum() {
        return Choices.PlanArea.fromId(area);
    }

    public void setAreaEnum(Choices.PlanArea value) {
        this.area = value.id();
    }

    public String getObjectives() {
        return objectives;
    }

    public void setObjectives(String objectives) {
        this.objectives = objectives == null ? "" : objectives;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getMaterials() {
        return materials;
    }

    public void setMaterials(String materials) {
        this.materials = materials == null ? "" : materials;
    }

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence == null ? "" : sequence;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions == null ? "" : instructions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? Choices.PlanStatus.PLANNED.id() : status;
    }

    public Choices.PlanStatus statusEnum() {
        return Choices.PlanStatus.fromId(status);
    }

    public void setStatusEnum(Choices.PlanStatus value) {
        this.status = value.id();
    }

    public String getReflection() {
        return reflection;
    }

    public void setReflection(String reflection) {
        this.reflection = reflection == null ? "" : reflection;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? "" : groupName;
    }

    /** "09:30 \u00b7 Language & literacy \u00b7 Completed" style meta line. */
    public String metaLine() {
        return Text.joinNonBlank(" \u00b7 ", timeLabel(), areaEnum().label(), statusEnum().label());
    }

    /** An independent copy, used when duplicating a plan or saving it as a template. */
    public LessonPlan clone() {
        LessonPlan c = new LessonPlan();
        c.id = id;
        c.groupId = groupId;
        c.title = title;
        c.date = date;
        c.endDate = endDate;
        c.startTime = startTime;
        c.endTime = endTime;
        c.area = area;
        c.objectives = objectives;
        c.description = description;
        c.materials = materials;
        c.sequence = sequence;
        c.instructions = instructions;
        c.status = status;
        c.reflection = reflection;
        c.template = template;
        c.recurring = recurring;
        c.sortOrder = sortOrder;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        c.groupName = groupName;
        return c;
    }
}
