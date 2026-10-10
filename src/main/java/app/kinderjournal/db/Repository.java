package app.kinderjournal.db;

import app.kinderjournal.model.Attendance;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.HistoryEntry;
import app.kinderjournal.model.Interview;
import app.kinderjournal.model.LessonPlan;
import app.kinderjournal.model.Observation;
import app.kinderjournal.model.Photo;
import app.kinderjournal.model.SearchHit;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single entry point to everything stored on this computer.
 *
 * <p>Screens talk to a {@code Repository}, never to a DAO directly, which keeps
 * the cross-module logic in one reviewable place: merging a child's timeline,
 * saving a register as one unit of work, and searching every kind of record.
 */
public class Repository implements AutoCloseable {

    private final Db db;
    private final GroupDao groups;
    private final ChildDao children;
    private final AttendanceDao attendance;
    private final ObservationDao observations;
    private final InterviewDao interviews;
    private final PlanDao plans;
    private final PhotoDao photos;

    public Repository(Db db) {
        this.db = db;
        this.groups = new GroupDao(db);
        this.children = new ChildDao(db);
        this.attendance = new AttendanceDao(db);
        this.observations = new ObservationDao(db);
        this.interviews = new InterviewDao(db);
        this.plans = new PlanDao(db);
        this.photos = new PhotoDao(db);
    }

    /** Opens the live database in the application data folder. */
    public static Repository open() {
        return new Repository(Db.open());
    }

    public Db db() {
        return db;
    }

    public GroupDao groups() {
        return groups;
    }

    public ChildDao children() {
        return children;
    }

    public AttendanceDao attendance() {
        return attendance;
    }

    public ObservationDao observations() {
        return observations;
    }

    public InterviewDao interviews() {
        return interviews;
    }

    public PlanDao plans() {
        return plans;
    }

    public PhotoDao photos() {
        return photos;
    }

    // -------------------------------------------------------------- first run

    /**
     * Prepares a brand-new data file: creates a sensible starting class so the
     * teacher never faces an empty, unusable class picker.
     *
     * @return the starting group when one had to be created, otherwise null
     */
    public Group ensureUsableStart() {
        Group existing = groups.firstActive();
        if (existing != null) {
            return existing;
        }
        Group group = new Group();
        group.setName("My class");
        group.setTeacher(db.setting("teacher_name", ""));
        group.setSchoolYear(schoolYearGuess());
        groups.insert(group);
        return group;
    }

    /** "2025/2026" style school year for the current date (starts in August). */
    public static String schoolYearGuess() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 8 ? today.getYear() : today.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    // ------------------------------------------------------------- attendance

    /**
     * The attendance register for a group on a date: every child in the group,
     * paired with whatever was recorded (or a fresh Present row when nothing was).
     *
     * <p>The roster is read from the profiles, so a child added this morning shows
     * up immediately, while a child who moved away stops appearing.
     */
    public List<RegisterRow> register(long groupId, LocalDate date) {
        List<Child> roster = children.listByGroup(groupId, false);
        Map<Long, Attendance> recorded = attendance.mapForGroupOnDate(groupId, date);
        List<RegisterRow> rows = new ArrayList<>(roster.size());
        for (Child child : roster) {
            Attendance a = recorded.get(child.getId());
            if (a == null) {
                a = new Attendance();
                a.setChildId(child.getId());
                a.setGroupId(groupId);
                a.setDate(Dates.toIso(date));
                a.setStatusEnum(Choices.AttendanceStatus.PRESENT);
            }
            rows.add(new RegisterRow(child, a, recorded.containsKey(child.getId())));
        }
        return rows;
    }

    /**
     * Saves a whole day's register in one transaction.
     *
     * <p>Either every mark lands or none does, so a failure halfway through cannot
     * leave the class half-registered.
     */
    public void saveRegister(long groupId, LocalDate date, List<RegisterRow> rows) {
        String iso = Dates.toIso(date);
        db.transaction(() -> {
            for (RegisterRow row : rows) {
                Attendance a = row.attendance();
                a.setGroupId(groupId);
                a.setDate(iso);
                attendance.save(a);
            }
            return null;
        });
    }

    /** One child's attendance on one date, or null. */
    public Attendance attendanceFor(long childId, LocalDate date) {
        return attendance.find(childId, Dates.toIso(date));
    }

    /** A register row: the child, their mark, and whether it was already recorded. */
    public record RegisterRow(Child child, Attendance attendance, boolean recorded) {

        public Choices.AttendanceStatus status() {
            return attendance.statusEnum();
        }

        public void setStatus(Choices.AttendanceStatus status) {
            attendance.setStatusEnum(status);
        }
    }

    // ------------------------------------------------------------- child history

    /**
     * Everything recorded about one child in a period, newest first.
     *
     * <p>This is what makes the Child History screen possible: attendance,
     * observations and interviews are merged into a single dated timeline instead
     * of the teacher visiting three separate sections.
     *
     * @param kinds      which record types to include; empty means all of them
     * @param category   optional observation category id filter ("" for all)
     */
    public List<HistoryEntry> childHistory(long childId, LocalDate from, LocalDate to,
                                           List<HistoryEntry.Kind> kinds, String category) {
        List<HistoryEntry> out = new ArrayList<>();
        List<HistoryEntry.Kind> wanted = (kinds == null || kinds.isEmpty())
                ? List.of(HistoryEntry.Kind.values())
                : kinds;

        if (wanted.contains(HistoryEntry.Kind.ATTENDANCE)) {
            out.addAll(attendance.historyForChild(childId, from, to));
        }
        if (wanted.contains(HistoryEntry.Kind.OBSERVATION)) {
            out.addAll(observations.historyForChild(childId, from, to,
                    Text.isBlank(category) ? null : category));
        }
        if (wanted.contains(HistoryEntry.Kind.INTERVIEW)) {
            out.addAll(interviews.historyForChild(childId, from, to, null));
        }
        if (wanted.contains(HistoryEntry.Kind.PLAN)) {
            Child child = children.find(childId);
            long groupId = child == null ? 0 : child.getGroupId();
            if (groupId > 0) {
                out.addAll(plans.historyForGroup(groupId, from, to));
            }
        }
        sortNewestFirst(out);
        return out;
    }

    /** Merges several timelines into one, newest first. */
    public static void sortNewestFirst(List<HistoryEntry> entries) {
        entries.sort(Comparator
                .comparing((HistoryEntry e) -> Text.isBlank(e.date()) ? "" : e.date())
                .reversed()
                .thenComparing(e -> e.kind().ordinal())
                .thenComparing(HistoryEntry::recordId, Comparator.reverseOrder()));
    }

    /** A count of records per kind, for the history summary chips. */
    public Map<HistoryEntry.Kind, Integer> historyCounts(long childId, LocalDate from, LocalDate to) {
        Map<HistoryEntry.Kind, Integer> counts = new LinkedHashMap<>();
        for (HistoryEntry.Kind kind : HistoryEntry.Kind.values()) {
            counts.put(kind, 0);
        }
        for (HistoryEntry entry : childHistory(childId, from, to, null, null)) {
            counts.merge(entry.kind(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Observation totals per category for a set of children.
     *
     * <p>Used by the category chips on the observations screen, which need the
     * totals for the class on screen rather than for the whole database.
     */
    public Map<String, Integer> observationCountsByCategory(List<Child> children) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Choices.ObservationCategory category : Choices.ObservationCategory.values()) {
            counts.put(category.id(), 0);
        }
        if (children == null || children.isEmpty()) {
            return counts;
        }
        for (Observation observation : observations.listForChildren(children, null, null, null, 0)) {
            counts.merge(observation.getCategory(), 1, Integer::sum);
        }
        return counts;
    }

    // -------------------------------------------------------------- child summary

    /**
     * The numbers shown on a child's profile: attendance totals plus how many
     * observations and interviews were written in the period.
     */
    public record ChildSummary(AttendanceDao.Summary attendance, int observations, int interviews,
                               int photos, String lastObservationDate) {
    }

    public ChildSummary summaryFor(long childId, LocalDate from, LocalDate to) {
        AttendanceDao.Summary att = attendance.summaryForChild(childId, from, to);
        int obs = observations.countForChild(childId, from, to);
        int ivs = interviews.countForChild(childId, from, to);
        int pic = photos.countForChild(childId);
        String lastObs = observations.listForChild(childId, from, to, null).stream()
                .map(Observation::getDate)
                .findFirst()
                .orElse("");
        return new ChildSummary(att, obs, ivs, pic, lastObs);
    }

    // -------------------------------------------------------------- child profile

    /**
     * Deletes a child and every trace of them, images included.
     *
     * <p>Run as one transaction for the rows, then the files are removed; a
     * partial failure leaves the database consistent and reports the problem.
     */
    public void deleteChildCompletely(long childId) {
        List<Photo> childPhotos = photos.listForChild(childId);
        String profilePhoto = children.find(childId) == null ? null : children.find(childId).getPhotoFile();
        db.transaction(() -> {
            db.update("DELETE FROM photos WHERE child_id = ?", childId);
            db.update("DELETE FROM observations WHERE child_id = ?", childId);
            db.update("DELETE FROM interviews WHERE child_id = ?", childId);
            db.update("DELETE FROM attendance WHERE child_id = ?", childId);
            db.update("DELETE FROM children WHERE id = ?", childId);
            return null;
        });
        for (Photo p : childPhotos) {
            deleteQuietly(AppPaths.photoFile(p.getFile()));
        }
        deleteQuietly(AppPaths.photoFile(profilePhoto));
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The rows are gone; a leftover image is harmless.
        }
    }

    /** Stores a profile photo and remembers its file name on the child. */
    public void setProfilePhoto(long childId, Path source) throws IOException {
        Child child = children.find(childId);
        String previous = child == null ? null : child.getPhotoFile();
        String stored = AppPaths.storePhoto(source);
        children.setPhotoFile(childId, stored);
        // Only remove the old image once the profile no longer references it.
        if (!Text.isBlank(previous) && !photos.fileInUse(previous)) {
            deleteQuietly(AppPaths.photoFile(previous));
        }
    }

    // ------------------------------------------------------------ observations

    /** Quick capture used by the dashboard and profile "add observation" action. */
    public long addObservation(long childId, LocalDate date, Choices.ObservationCategory category,
                               String title, String body) {
        Observation o = new Observation();
        o.setChildId(childId);
        Child child = children.find(childId);
        o.setGroupId(child == null ? 0 : child.getGroupId());
        o.setDate(Dates.toIso(date == null ? LocalDate.now() : date));
        o.setCategoryEnum(category == null ? Choices.ObservationCategory.GENERAL : category);
        o.setTitle(Text.clean(title));
        o.setBody(Text.clean(body));
        observations.insert(o);
        return o.getId();
    }

    /** Deletes an observation along with its photos. */
    public void deleteObservation(long observationId) {
        photos.deleteFor(Photo.OwnerType.OBSERVATION, observationId);
        observations.delete(observationId);
    }

    /** Deletes an interview along with its photos. */
    public void deleteInterview(long interviewId) {
        photos.deleteFor(Photo.OwnerType.INTERVIEW, interviewId);
        interviews.delete(interviewId);
    }

    // ------------------------------------------------------------------ search

    /**
     * Searches every kind of record at once.
     *
     * <p>Results are matched case-insensitively on the stored text and returned
     * newest first, each with a short snippet around the match so the teacher can
     * tell two similar entries apart without opening both.
     */
    public List<SearchHit> search(SearchQuery query) {
        List<SearchHit> hits = new ArrayList<>();
        String keyword = query.keyword();
        boolean hasKeyword = !Text.isBlank(keyword);

        // Children themselves match on name, so "anna" finds the profile.
        if (hasKeyword && query.includeChildren()) {
            for (Child child : children.searchByName(keyword)) {
                if (query.groupId() > 0 && child.getGroupId() != query.groupId()) {
                    continue;
                }
                if (query.childId() > 0 && child.getId() != query.childId()) {
                    continue;
                }
                hits.add(new SearchHit("", HistoryEntry.Kind.NOTE, child.getId(), child.getId(),
                        child.getGroupId(), child.fullName(), child.getGroupName(),
                        "Child profile", buildSnippet(
                                Text.joinNonBlank(" \u00b7 ", child.birthSummary(),
                                        child.getGuardianName()),
                                keyword)));
            }
        }

        if (query.includeObservations()) {
            for (Observation o : observations.search(keyword, query.childIdOrNull(),
                    query.groupIdOrNull(), query.from(), query.to(), query.category(),
                    query.limit())) {
                Child child = children.find(o.getChildId());
                hits.add(new SearchHit(o.getDate(),
                        hasKeyword ? HistoryEntry.Kind.OBSERVATION : HistoryEntry.Kind.OBSERVATION,
                        o.getId(), o.getChildId(), o.getGroupId(),
                        child == null ? o.getChildName() : child.fullName(),
                        child == null ? "" : child.getGroupName(),
                        Text.joinNonBlank(" \u00b7 ", o.categoryEnum().label(), o.getTitle()),
                        buildSnippet(o.getBody(), keyword)));
            }
        }

        if (query.includeInterviews()) {
            for (Interview i : interviews.search(keyword, query.childIdOrNull(),
                    query.groupIdOrNull(), query.from(), query.to(), query.interviewType(),
                    query.limit())) {
                Child child = children.find(i.getChildId());
                hits.add(new SearchHit(i.getDate(), HistoryEntry.Kind.INTERVIEW, i.getId(),
                        i.getChildId(), i.getGroupId(),
                        child == null ? i.getChildName() : child.fullName(),
                        child == null ? "" : child.getGroupName(),
                        i.typeEnum().label(),
                        buildSnippet(Text.joinNonBlank(" \u00b7 ", i.getTopics(), i.getDiscussed(),
                                i.getConclusions()), keyword)));
            }
        }

        if (query.includePlans()) {
            for (LessonPlan p : plans.search(keyword, query.groupIdOrNull(), query.from(),
                    query.to(), query.planStatus(), query.planArea(), query.limit())) {
                hits.add(new SearchHit(p.getDate(), HistoryEntry.Kind.PLAN, p.getId(),
                        0, p.getGroupId(), p.getGroupName(), p.getGroupName(),
                        Text.joinNonBlank(" \u00b7 ", p.getTitle(), p.areaEnum().label()),
                        buildSnippet(Text.joinNonBlank(" \u00b7 ", p.getObjectives(),
                                p.getDescription()), keyword)));
            }
        }

        // Newest first, then by how specific the match was.
        hits.sort(Comparator
                .comparing((SearchHit h) -> Text.isBlank(h.date()) ? "" : h.date())
                .reversed()
                .thenComparing(SearchHit::kind));
        if (query.limit() > 0 && hits.size() > query.limit()) {
            return new ArrayList<>(hits.subList(0, query.limit()));
        }
        return hits;
    }

    /** Builds a short snippet, centring on the first occurrence of the keyword. */
    public static String buildSnippet(String text, String keyword) {
        String flat = Text.clean(text).replaceAll("\\s+", " ");
        if (flat.isEmpty()) {
            return "";
        }
        if (Text.isBlank(keyword)) {
            return Text.summarise(flat, 140);
        }
        int at = Text.lower(flat).indexOf(Text.lower(keyword));
        if (at < 0) {
            return Text.summarise(flat, 140);
        }
        int start = Math.max(0, at - 50);
        int end = Math.min(flat.length(), at + keyword.length() + 90);
        String snippet = flat.substring(start, end).trim();
        if (start > 0) {
            snippet = "\u2026" + snippet;
        }
        if (end < flat.length()) {
            snippet = snippet + "\u2026";
        }
        return snippet;
    }

    /**
     * The criteria for an advanced search.
     *
     * @param keyword      free text, matched against every text field
     * @param childId      0 or less means "any child"
     * @param groupId      0 or less means "any class"
     * @param category     observation category id, blank/"all" for every category
     * @param interviewType interview type id, blank/"all" for every type
     * @param planStatus   plan status id, blank/"all" for every status
     * @param planArea     plan area id, blank/"all" for every area
     */
    public record SearchQuery(String keyword, long childId, long groupId, LocalDate from, LocalDate to,
                              String category, String interviewType, String planStatus, String planArea,
                              boolean includeChildren, boolean includeObservations,
                              boolean includeInterviews, boolean includePlans, int limit) {

        public Long childIdOrNull() {
            return childId > 0 ? childId : null;
        }

        public Long groupIdOrNull() {
            return groupId > 0 ? groupId : null;
        }

        /** A query that matches every record type, for the default search screen. */
        public static SearchQuery everything(String keyword) {
            return new SearchQuery(keyword, 0, 0, null, null, "", "", "", "",
                    true, true, true, true, 500);
        }

        /** True when nothing at all has been asked for. */
        public boolean isEmpty() {
            return Text.isBlank(keyword) && childId <= 0 && groupId <= 0 && from == null && to == null
                    && Text.isBlank(category) && Text.isBlank(interviewType)
                    && Text.isBlank(planStatus) && Text.isBlank(planArea);
        }
    }

    // ------------------------------------------------------- maintenance queries

    /** A one-line description of where the data lives, for the About/backup screen. */
    public String dataLocationText() {
        return AppPaths.dataDir().toAbsolutePath().toString();
    }

    /** Counts of every record type, shown on the dashboard and About screen. */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("Classes", (int) db.count("SELECT COUNT(*) AS n FROM groups"));
        out.put("Children", (int) db.count("SELECT COUNT(*) AS n FROM children WHERE archived = 0"));
        out.put("Attendance records", (int) db.count("SELECT COUNT(*) AS n FROM attendance"));
        out.put("Observations", (int) db.count("SELECT COUNT(*) AS n FROM observations"));
        out.put("Interviews", (int) db.count("SELECT COUNT(*) AS n FROM interviews"));
        out.put("Lesson plans", (int) db.count("SELECT COUNT(*) AS n FROM lesson_plans WHERE template = 0"));
        out.put("Photos", (int) db.count("SELECT COUNT(*) AS n FROM photos"));
        return out;
    }

    @Override
    public void close() {
        db.close();
    }
}
