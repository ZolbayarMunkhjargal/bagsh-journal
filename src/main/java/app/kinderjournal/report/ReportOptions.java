package app.kinderjournal.report;

import app.kinderjournal.model.HistoryEntry;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the teacher can choose before generating a document.
 *
 * <p>One options object drives every report, so the report screen has a single
 * form rather than one per document type. Fields that do not apply to the chosen
 * report are simply ignored by the renderer.
 */
public final class ReportOptions {

    private final ReportType type;
    private final long groupId;
    private final long childId;
    private final LocalDate from;
    private final LocalDate to;
    private final List<HistoryEntry.Kind> kinds;
    private final String category;
    private final String keyword;
    private final String teacherName;
    private final String notes;
    private final boolean includeProfile;
    private final boolean includeAttendanceSummary;
    private final boolean includeAttendanceDetail;
    private final boolean includeObservations;
    private final boolean includeInterviews;
    private final boolean includePlans;
    private final boolean includePhotos;
    private final boolean includeConfidential;
    private final boolean landscape;
    private final List<Long> childIds;

    private ReportOptions(Builder builder) {
        this.type = builder.type;
        this.groupId = builder.groupId;
        this.childId = builder.childId;
        this.from = builder.from;
        this.to = builder.to;
        this.kinds = builder.kinds;
        this.category = builder.category;
        this.keyword = builder.keyword;
        this.teacherName = builder.teacherName;
        this.notes = builder.notes;
        this.includeProfile = builder.includeProfile;
        this.includeAttendanceSummary = builder.includeAttendanceSummary;
        this.includeAttendanceDetail = builder.includeAttendanceDetail;
        this.includeObservations = builder.includeObservations;
        this.includeInterviews = builder.includeInterviews;
        this.includePlans = builder.includePlans;
        this.includePhotos = builder.includePhotos;
        this.includeConfidential = builder.includeConfidential;
        this.landscape = builder.landscape;
        this.childIds = builder.childIds;
    }

    public ReportType type() {
        return type;
    }

    /** 0 means "every class". */
    public long groupId() {
        return groupId;
    }

    /** 0 means "not a single-child report". */
    public long childId() {
        return childId;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public List<HistoryEntry.Kind> kinds() {
        return kinds;
    }

    public String category() {
        return category;
    }

    public String keyword() {
        return keyword;
    }

    public String teacherName() {
        return teacherName;
    }

    /** Free text the teacher can add to the printed document, such as a summary. */
    public String notes() {
        return notes;
    }

    public boolean includeProfile() {
        return includeProfile;
    }

    public boolean includeAttendanceSummary() {
        return includeAttendanceSummary;
    }

    public boolean includeAttendanceDetail() {
        return includeAttendanceDetail;
    }

    public boolean includeObservations() {
        return includeObservations;
    }

    public boolean includeInterviews() {
        return includeInterviews;
    }

    public boolean includePlans() {
        return includePlans;
    }

    public boolean includePhotos() {
        return includePhotos;
    }

    public boolean includeConfidential() {
        return includeConfidential;
    }

    /** True for reports better printed sideways (weekly plans, wide registers). */
    public boolean landscape() {
        return landscape;
    }

    /** Specific children to include; empty means "the whole class". */
    public List<Long> childIds() {
        return childIds;
    }

    public static Builder builder(ReportType type) {
        return new Builder(type);
    }

    /** A copy of these options aimed at a single child. */
    public ReportOptions forChild(long newChildId) {
        return new Builder(type)
                .group(groupId).child(newChildId).range(from, to).kinds(kinds).category(category)
                .keyword(keyword).teacher(teacherName).notes(notes)
                .profile(includeProfile).attendanceSummary(includeAttendanceSummary)
                .attendanceDetail(includeAttendanceDetail).observations(includeObservations)
                .interviews(includeInterviews).plans(includePlans).photos(includePhotos)
                .confidential(includeConfidential).landscape(landscape).children(childIds)
                .build();
    }

    /** Fluent builder so callers only set what they care about. */
    public static final class Builder {
        private final ReportType type;
        private long groupId;
        private long childId;
        private LocalDate from;
        private LocalDate to;
        private List<HistoryEntry.Kind> kinds = List.of();
        private String category = "";
        private String keyword = "";
        private String teacherName = "";
        private String notes = "";
        private boolean includeProfile = true;
        private boolean includeAttendanceSummary = true;
        private boolean includeAttendanceDetail;
        private boolean includeObservations = true;
        private boolean includeInterviews = true;
        private boolean includePlans;
        private boolean includePhotos = true;
        private boolean includeConfidential;
        private boolean landscape;
        private List<Long> childIds = List.of();

        private Builder(ReportType type) {
            this.type = type;
            if (type != null) {
                this.landscape = type.defaultLandscape();
            }
        }

        public Builder group(long value) {
            this.groupId = value;
            return this;
        }

        public Builder child(long value) {
            this.childId = value;
            return this;
        }

        public Builder range(LocalDate fromDate, LocalDate toDate) {
            this.from = fromDate;
            this.to = toDate;
            return this;
        }

        public Builder kinds(List<HistoryEntry.Kind> value) {
            this.kinds = value == null ? List.of() : value;
            return this;
        }

        public Builder category(String value) {
            this.category = value == null ? "" : value;
            return this;
        }

        public Builder keyword(String value) {
            this.keyword = value == null ? "" : value;
            return this;
        }

        public Builder teacher(String value) {
            this.teacherName = value == null ? "" : value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value == null ? "" : value;
            return this;
        }

        public Builder profile(boolean value) {
            this.includeProfile = value;
            return this;
        }

        public Builder attendanceSummary(boolean value) {
            this.includeAttendanceSummary = value;
            return this;
        }

        public Builder attendanceDetail(boolean value) {
            this.includeAttendanceDetail = value;
            return this;
        }

        public Builder observations(boolean value) {
            this.includeObservations = value;
            return this;
        }

        public Builder interviews(boolean value) {
            this.includeInterviews = value;
            return this;
        }

        public Builder plans(boolean value) {
            this.includePlans = value;
            return this;
        }

        public Builder photos(boolean value) {
            this.includePhotos = value;
            return this;
        }

        public Builder confidential(boolean value) {
            this.includeConfidential = value;
            return this;
        }

        public Builder landscape(boolean value) {
            this.landscape = value;
            return this;
        }

        public Builder children(List<Long> value) {
            this.childIds = value == null ? List.of() : value;
            return this;
        }

        public ReportOptions build() {
            return new ReportOptions(this);
        }
    }
}
