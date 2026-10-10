package app.kinderjournal.model;

import app.kinderjournal.util.Text;

/** A dated observation about one child, optionally tied to a development category. */
public class Observation {

    private long id;
    private long childId;
    private long groupId;             // group at the time of writing (for filtering)
    private String date = "";         // ISO yyyy-MM-dd
    private String category = Choices.ObservationCategory.GENERAL.id();
    private String title = "";
    private String body = "";
    private String context = "";      // e.g. "morning circle", "outdoor play"
    private String author = "";       // who wrote it
    private boolean confidential;
    private String createdAt = "";
    private String updatedAt = "";

    /** Filled in by list queries (not stored). */
    private String childName = "";
    private int photoCount = -1;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? Choices.ObservationCategory.GENERAL.id() : category;
    }

    public Choices.ObservationCategory categoryEnum() {
        return Choices.ObservationCategory.fromId(category);
    }

    public void setCategoryEnum(Choices.ObservationCategory value) {
        this.category = value.id();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body == null ? "" : body;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context == null ? "" : context;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author == null ? "" : author;
    }

    public boolean isConfidential() {
        return confidential;
    }

    public void setConfidential(boolean confidential) {
        this.confidential = confidential;
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

    public String getChildName() {
        return childName;
    }

    public void setChildName(String childName) {
        this.childName = childName == null ? "" : childName;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    /** Headline shown in lists: the explicit title, else the first words of the body. */
    public String headline() {
        if (!title.isBlank()) {
            return title.trim();
        }
        return Text.summarise(body, 70);
    }

    /** Short one-line preview for history and search results. */
    public String preview(int max) {
        return Text.summarise(body, max);
    }
}
