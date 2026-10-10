package app.kinderjournal.model;

import app.kinderjournal.util.Text;

/** A recorded conversation with a parent, guardian, child or colleague. */
public class Interview {

    private long id;
    private long childId;
    private long groupId;
    private String date = "";
    private String type = Choices.InterviewType.PARENT.id();
    private String mode = Choices.InterviewMode.IN_PERSON.id();
    private String participants = "";
    private String topics = "";
    private String discussed = "";     // what was shared during the conversation
    private String conclusions = "";   // the teacher's notes and conclusions
    private String followUp = "";      // actions to take / revisit later
    private String followUpDate;       // when to revisit, may be null
    private boolean followUpDone;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? Choices.InterviewType.OTHER.id() : type;
    }

    public Choices.InterviewType typeEnum() {
        return Choices.InterviewType.fromId(type);
    }

    public void setTypeEnum(Choices.InterviewType value) {
        this.type = value.id();
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? Choices.InterviewMode.IN_PERSON.id() : mode;
    }

    public Choices.InterviewMode modeEnum() {
        return Choices.InterviewMode.fromId(mode);
    }

    public void setModeEnum(Choices.InterviewMode value) {
        this.mode = value.id();
    }

    public String getParticipants() {
        return participants;
    }

    public void setParticipants(String participants) {
        this.participants = participants == null ? "" : participants;
    }

    public String getTopics() {
        return topics;
    }

    public void setTopics(String topics) {
        this.topics = topics == null ? "" : topics;
    }

    public String getDiscussed() {
        return discussed;
    }

    public void setDiscussed(String discussed) {
        this.discussed = discussed == null ? "" : discussed;
    }

    public String getConclusions() {
        return conclusions;
    }

    public void setConclusions(String conclusions) {
        this.conclusions = conclusions == null ? "" : conclusions;
    }

    public String getFollowUp() {
        return followUp;
    }

    public void setFollowUp(String followUp) {
        this.followUp = followUp == null ? "" : followUp;
    }

    public String getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(String followUpDate) {
        this.followUpDate = Text.nullIfBlank(followUpDate);
    }

    public boolean isFollowUpDone() {
        return followUpDone;
    }

    public void setFollowUpDone(boolean followUpDone) {
        this.followUpDone = followUpDone;
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

    /** Headline for lists: the topics, else the first words of what was discussed. */
    public String headline() {
        if (!topics.isBlank()) {
            return Text.summarise(topics, 80);
        }
        return Text.summarise(discussed, 80);
    }

    /** True when a follow-up is outstanding and its date has arrived or passed. */
    public boolean isFollowUpDue() {
        if (followUpDone || Text.isBlank(followUp) || Text.isBlank(followUpDate)) {
            return false;
        }
        java.time.LocalDate due = app.kinderjournal.util.Dates.fromIso(followUpDate);
        return due != null && !due.isAfter(java.time.LocalDate.now());
    }
}
