package app.kinderjournal.tools;

import app.kinderjournal.db.AttendanceDao;
import app.kinderjournal.i18n.Lang;
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.db.ChildDao;
import app.kinderjournal.db.Db;
import app.kinderjournal.db.GroupDao;
import app.kinderjournal.db.InterviewDao;
import app.kinderjournal.db.ObservationDao;
import app.kinderjournal.db.PlanDao;
import app.kinderjournal.db.PhotoDao;
import app.kinderjournal.db.Repository;
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
import java.util.List;
import java.util.Map;

/**
 * Headless self-tests for the data layer.
 *
 * <p>These run without a display, so `tools\test.ps1` works on a build machine or
 * over a remote session. They cover the rules that matter most: that records are
 * tied to the right child and date, that correcting a mark replaces the old one,
 * that a child's history merges every record type, and that deleting a child
 * takes their records and photos with them.
 */
public final class SelfTest {

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("kinderjournal-selftest");
        System.setProperty("kinderjournal.dataDir", temp.toString());
        System.out.println("KinderJournal self-tests");
        System.out.println("  data folder: " + AppPaths.dataDir());
        System.out.println();

        try (Db db = Db.openAt(temp.resolve("test.db"))) {
            Repository repo = new Repository(db);

            testSchemaAndIntegrity(repo);
            testGroups(repo);
            testChildren(repo);
            testAttendance(repo);
            testAttendanceCorrection(repo);
            testObservations(repo);
            testInterviews(repo);
            testLessonPlans(repo);
            testPhotos(repo, temp);
            testChildHistory(repo);
            testSearch(repo);
            testReferentialIntegrity(repo);
            testDeletionCascade(repo);
            testDatesAndText();
            testTranslationCatalogues();
        }

        System.out.println();
        System.out.printf("%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.out.println();
            for (String failure : failures) {
                System.out.println("  FAILED: " + failure);
            }
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------- tests

    private static void testSchemaAndIntegrity(Repository repo) {
        check("integrity check on a fresh database", "ok".equals(repo.db().integrityCheck()));
        long tables = repo.db().count("SELECT COUNT(*) AS n FROM sqlite_master WHERE type = 'table'");
        check("all tables created", tables >= 7);
        check("foreign keys enabled",
                repo.db().one("PRAGMA foreign_keys", row -> row.getLong("foreign_keys")) == 1L);
    }

    private static void testGroups(Repository repo) {
        GroupDao groups = repo.groups();

        Group sunflowers = new Group();
        sunflowers.setName("Sunflower group");
        sunflowers.setAgeRange("4-5");
        sunflowers.setRoom("Room 2");
        sunflowers.setSchoolYear("2025/2026");
        groups.insert(sunflowers);
        check("group insert assigns an id", sunflowers.getId() > 0);

        Group acorns = new Group();
        acorns.setName("Acorn group");
        groups.insert(acorns);

        check("groups listed", groups.listActive().size() == 2);
        check("group lookup by id", groups.find(sunflowers.getId()).getName().equals("Sunflower group"));
        check("group lookup by name is case-insensitive",
                groups.findByName("sunflower GROUP") != null);
        check("group subtitle built", sunflowers.subtitle().contains("Room 2"));

        sunflowers.setName("Sunflowers");
        groups.update(sunflowers);
        check("group rename saved", groups.find(sunflowers.getId()).getName().equals("Sunflowers"));

        check("groups can be reordered", reorderWorks(groups, sunflowers, acorns));
    }

    private static boolean reorderWorks(GroupDao groups, Group first, Group second) {
        groups.reorder(List.of(second.getId(), first.getId()));
        List<Group> ordered = groups.listActive();
        boolean ok = ordered.get(0).getId() == second.getId();
        groups.reorder(List.of(first.getId(), second.getId()));
        return ok;
    }

    private static void testChildren(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        ChildDao children = repo.children();

        Child anna = newChild("Anna", "Berg", "2020-03-12", group.getId());
        children.insert(anna);
        check("child insert assigns an id", anna.getId() > 0);
        check("child full name", anna.fullName().equals("Anna Berg"));
        check("child sort name", anna.sortName().equals("Berg, Anna"));
        check("child age computed", anna.ageYears() >= 5);
        check("child initials", anna.initials().equals("AB"));

        Child ben = newChild("Ben", "Olsen", "2021-07-01", group.getId());
        children.insert(ben);
        Child cara = newChild("Cara", "Meyer", "2020-11-20", 0);
        children.insert(cara);

        check("children listed for group", children.listByGroup(group.getId(), false).size() == 2);
        check("unassigned children listed", children.listByGroup(-1, false).size() == 1);
        check("all children listed", children.listAll().size() == 3);
        check("roster sorted by last name",
                children.listByGroup(group.getId(), false).get(0).getLastName().equals("Berg"));
        check("group name joined onto child",
                children.find(anna.getId()).getGroupName().equals("Sunflowers"));

        check("name search finds a child", children.searchByName("ann").size() == 1);
        check("name search is case-insensitive", children.searchByName("OLSEN").size() == 1);

        anna.setGuardianName("Maria Berg");
        anna.setGuardianPhone("555-0101");
        anna.setAllergies("peanuts");
        children.update(anna);
        Child reloaded = children.find(anna.getId());
        check("child profile saved", reloaded.getGuardianName().equals("Maria Berg")
                && reloaded.getAllergies().equals("peanuts"));

        children.moveToGroup(cara.getId(), group.getId());
        check("child moved between classes", children.find(cara.getId()).getGroupId() == group.getId());
        children.moveToGroup(cara.getId(), 0);
        check("child can be unassigned", children.find(cara.getId()).getGroupId() == 0);
    }

    private static Child newChild(String first, String last, String birth, long groupId) {
        Child child = new Child();
        child.setFirstName(first);
        child.setLastName(last);
        child.setBirthDate(birth);
        child.setGroupId(groupId);
        return child;
    }

    private static void testAttendance(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        AttendanceDao attendance = repo.attendance();
        LocalDate day = LocalDate.of(2026, 3, 2);

        List<Repository.RegisterRow> register = repo.register(group.getId(), day);
        check("register lists the group's children", register.size() == 2);
        check("register starts as Present", register.get(0).status() == Choices.AttendanceStatus.PRESENT);
        check("register marks nothing as recorded yet",
                register.stream().noneMatch(Repository.RegisterRow::recorded));

        register.get(0).setStatus(Choices.AttendanceStatus.ABSENT);
        register.get(0).attendance().setReason("unwell");
        register.get(1).setStatus(Choices.AttendanceStatus.LATE);
        repo.saveRegister(group.getId(), day, register);

        Map<Long, Attendance> saved = attendance.mapForGroupOnDate(group.getId(), day);
        check("register saved both rows", saved.size() == 2);
        check("absent reason stored", saved.get(register.get(0).child().getId()).getReason().equals("unwell"));
        check("late status stored",
                saved.get(register.get(1).child().getId()).statusEnum() == Choices.AttendanceStatus.LATE);

        AttendanceDao.Summary summary = attendance.summaryForGroup(group.getId(), day, day);
        check("summary counts absence", summary.absent() == 1);
        check("summary counts late", summary.late() == 1);
        check("summary attendance rate", summary.ratePercent() == 50);
        check("summary recorded total", summary.recorded() == 2);

        AttendanceDao.Summary childSummary = attendance.summaryForChild(
                register.get(0).child().getId(), day, day);
        check("per-child summary", childSummary.absent() == 1 && childSummary.recorded() == 1);

        check("recorded dates listed", attendance.recordedDates(group.getId(), null, null).size() == 1);
        check("most recent date found",
                Dates.toIso(day).equals(attendance.mostRecentDate(group.getId())));

        LocalDate later = day.plusDays(1);
        List<Repository.RegisterRow> nextDay = repo.register(group.getId(), later);
        nextDay.get(0).setStatus(Choices.AttendanceStatus.PRESENT);
        nextDay.get(1).setStatus(Choices.AttendanceStatus.PRESENT);
        repo.saveRegister(group.getId(), later, nextDay);
        check("two days recorded", attendance.listForGroup(group.getId(), null, null).size() == 4);
        check("range query respects bounds",
                attendance.listForGroup(group.getId(), later, later).size() == 2);
        check("per-day totals grouped",
                attendance.summariesByDate(group.getId(), null, null).size() == 2);
    }

    private static void testAttendanceCorrection(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        AttendanceDao attendance = repo.attendance();
        List<Child> roster = repo.children().listByGroup(group.getId(), false);
        Child child = roster.get(0);
        LocalDate day = LocalDate.of(2026, 3, 2);

        Attendance attendanceRow = attendance.find(child.getId(), Dates.toIso(day));
        check("existing record found", attendanceRow != null);
        check("record starts absent", attendanceRow.statusEnum() == Choices.AttendanceStatus.ABSENT);

        attendanceRow.setStatusEnum(Choices.AttendanceStatus.EXCUSED);
        attendanceRow.setReason("family trip");
        attendance.save(attendanceRow);

        Attendance corrected = attendance.find(child.getId(), Dates.toIso(day));
        check("correction replaces the old mark",
                corrected.statusEnum() == Choices.AttendanceStatus.EXCUSED);
        check("correction keeps one row per child per day",
                attendance.listForChild(child.getId(), null, null).size() == 2);
        check("corrected reason stored", corrected.getReason().equals("family trip"));

        attendance.delete(child.getId(), Dates.toIso(day));
        check("record deleted", attendance.find(child.getId(), Dates.toIso(day)) == null);
        // Put it back so later tests still see a populated day.
        attendanceRow.setId(0);
        attendance.save(attendanceRow);
    }

    private static void testObservations(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        Child child = repo.children().listByGroup(group.getId(), false).get(0);
        ObservationDao observations = repo.observations();

        Observation first = new Observation();
        first.setChildId(child.getId());
        first.setGroupId(group.getId());
        first.setDate("2026-03-02");
        first.setCategoryEnum(Choices.ObservationCategory.LANGUAGE);
        first.setTitle("New words at circle time");
        first.setBody("Used three new words during show and tell and asked a question about "
                + "the story. Confident communication in front of the group.");
        first.setContext("morning circle");
        observations.insert(first);
        check("observation insert assigns an id", first.getId() > 0);

        Observation second = new Observation();
        second.setChildId(child.getId());
        second.setGroupId(group.getId());
        second.setDate("2026-03-05");
        second.setCategoryEnum(Choices.ObservationCategory.SOCIAL);
        second.setBody("Shared the building blocks without being asked.");
        observations.insert(second);

        check("observations listed for a child",
                observations.listForChild(child.getId(), null, null, null).size() == 2);
        check("filter by category",
                observations.listForChild(child.getId(), null, null, "social").size() == 1);
        check("filter by date range",
                observations.listForChild(child.getId(), LocalDate.of(2026, 3, 4), null, null).size() == 1);
        check("group observation list", observations.listForGroup(group.getId(), null, null, null).size() == 2);
        check("headline falls back to the body", second.headline().startsWith("Shared the building"));
        check("count for child", observations.countForChild(child.getId(), null, null) == 2);

        Map<String, Integer> byCategory = observations.countByCategory(child.getId(), null, null);
        check("category totals", byCategory.getOrDefault("social", 0) == 1
                && byCategory.getOrDefault("language", 0) == 1);

        second.setBody("Shared the building blocks and invited another child to join in.");
        observations.update(second);
        check("observation edit saved",
                observations.find(second.getId()).getBody().contains("invited another child"));

        check("countOn finds today's observation", observations.countOn(LocalDate.of(2026, 3, 5)) == 1);

        Observation deleteTarget = new Observation();
        deleteTarget.setChildId(child.getId());
        deleteTarget.setDate("2026-03-06");
        deleteTarget.setBody("temporary");
        observations.insert(deleteTarget);
        observations.delete(deleteTarget.getId());
        check("observation deleted", observations.find(deleteTarget.getId()) == null);
    }

    private static void testInterviews(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        Child child = repo.children().listByGroup(group.getId(), false).get(0);
        InterviewDao interviews = repo.interviews();

        Interview interview = new Interview();
        interview.setChildId(child.getId());
        interview.setGroupId(group.getId());
        interview.setDate("2026-03-03");
        interview.setTypeEnum(Choices.InterviewType.PARENT);
        interview.setParticipants("Maria Berg (mother)");
        interview.setTopics("Adjustment to kindergarten, sleep routine");
        interview.setDiscussed("Maria reports Anna settles well but is tired after lunch.");
        interview.setConclusions("No concerns about the transition.");
        interview.setFollowUp("Check tiredness in two weeks and report back");
        interview.setFollowUpDate("2026-03-17");
        interviews.insert(interview);
        check("interview insert assigns an id", interview.getId() > 0);

        check("interviews listed for a child",
                interviews.listForChild(child.getId(), null, null, null).size() == 1);
        check("interview detail includes conclusions",
                interviews.historyForChild(child.getId(), null, null, null).get(0)
                        .detail().contains("No concerns"));
        check("interview headline uses topics",
                interview.headline().startsWith("Adjustment"));
        check("count for child", interviews.countForChild(child.getId(), null, null) == 1);

        Interview due = new Interview();
        due.setChildId(child.getId());
        due.setDate("2026-03-04");
        due.setTypeEnum(Choices.InterviewType.CHILD);
        due.setTopics("Talked about favourite activities");
        due.setFollowUp("Provide more building materials");
        due.setFollowUpDate(Dates.toIso(LocalDate.now().minusDays(1)));
        interviews.insert(due);

        List<Interview> dueList = interviews.listDueFollowUps(0, 10);
        check("outstanding follow-up listed", dueList.stream()
                .anyMatch(i -> i.getId() == due.getId()));
        interviews.setFollowUpDone(due.getId(), true);
        check("completed follow-up hidden", interviews.listDueFollowUps(0, 10).stream()
                .noneMatch(i -> i.getId() == due.getId()));
        check("follow-up due flag", !due.isFollowUpDue() || !due.isFollowUpDone());
    }

    private static void testLessonPlans(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        PlanDao plans = repo.plans();
        LocalDate monday = LocalDate.of(2026, 3, 2);

        LessonPlan dayPlan = new LessonPlan();
        dayPlan.setGroupId(group.getId());
        dayPlan.setTitle("Story sequencing");
        dayPlan.setDate(Dates.toIso(monday));
        dayPlan.setStartTime("09:30");
        dayPlan.setEndTime("10:15");
        dayPlan.setAreaEnum(Choices.PlanArea.LANGUAGE);
        dayPlan.setObjectives("Retell a story in the right order");
        dayPlan.setMaterials("Picture cards, felt board");
        dayPlan.setSequence("1. Read the story\n2. Order the cards\n3. Retell in pairs");
        plans.insert(dayPlan);
        check("plan insert assigns an id", dayPlan.getId() > 0);

        LessonPlan weekPlan = new LessonPlan();
        weekPlan.setGroupId(group.getId());
        weekPlan.setTitle("Spring project");
        weekPlan.setDate(Dates.toIso(monday));
        weekPlan.setEndDate(Dates.toIso(monday.plusDays(4)));
        weekPlan.setAreaEnum(Choices.PlanArea.SCIENCE);
        plans.insert(weekPlan);

        check("plans listed for a day", plans.listForDay(group.getId(), monday).size() == 2);
        check("multi-day plan covers later days",
                plans.listForDay(group.getId(), monday.plusDays(3)).size() == 1);
        check("weekly range", plans.listBetween(group.getId(), monday, monday.plusDays(6)).size() == 2);

        Map<String, List<LessonPlan>> byDay = plans.byDay(group.getId(), monday, monday.plusDays(6));
        check("weekly layout expands range onto each day", byDay.size() == 5);
        check("range label", weekPlan.isRange() && weekPlan.dateRangeLabel().contains("\u2013"));
        check("time label", dayPlan.timeLabel().equals("09:30 \u2013 10:15"));

        plans.setStatusAndReflection(dayPlan.getId(), Choices.PlanStatus.COMPLETED.id(),
                "Children enjoyed the card ordering.");
        LessonPlan reloaded = plans.find(dayPlan.getId());
        check("status and reflection saved",
                reloaded.statusEnum() == Choices.PlanStatus.COMPLETED
                        && reloaded.getReflection().contains("enjoyed"));

        check("upcoming plans listed", !plans.listUpcoming(group.getId(), monday, 5).isEmpty());

        long copyId = plans.duplicate(dayPlan.getId(), monday.plusDays(7), false);
        LessonPlan copy = plans.find(copyId);
        check("plan duplicated onto a new date",
                copy != null && copy.getDate().equals(Dates.toIso(monday.plusDays(7))));
        check("duplicate starts as planned",
                copy.statusEnum() == Choices.PlanStatus.PLANNED && copy.getReflection().isEmpty());

        long templateId = plans.duplicate(dayPlan.getId(), null, true);
        check("template saved", plans.find(templateId).isTemplate());
        check("templates excluded from the calendar",
                plans.listForDay(group.getId(), monday).stream()
                        .noneMatch(LessonPlan::isTemplate));

        int shifted = plans.shiftRange(group.getId(), monday, monday.plusDays(1), 7);
        check("range shift moved plans", shifted >= 1);
        check("shifted plan on the new date",
                plans.find(copyId).getDate().equals(Dates.toIso(monday.plusDays(7))));

        check("status count", plans.countByStatus(group.getId(), monday, monday.plusDays(20),
                Choices.PlanStatus.COMPLETED.id()) >= 1);
    }

    private static void testPhotos(Repository repo, Path temp) throws IOException {
        Group group = repo.groups().findByName("Sunflowers");
        Child child = repo.children().listByGroup(group.getId(), false).get(0);
        PhotoDao photos = repo.photos();

        Path source = temp.resolve("sample-photo.png");
        Files.write(source, minimalPng());

        Observation observation = repo.observations()
                .listForChild(child.getId(), null, null, null).get(0);
        Photo photo = photos.add(Photo.OwnerType.OBSERVATION, observation.getId(), child.getId(),
                source, "Ordering the story cards");
        check("photo row created", photo.getId() > 0);
        check("photo file copied into the data folder", photo.fileExists());
        check("photo caption stored", photo.getCaption().equals("Ordering the story cards"));
        check("photo counted for the observation",
                photos.countFor(Photo.OwnerType.OBSERVATION, observation.getId()) == 1);
        check("photo counted for the child", photos.countForChild(child.getId()) == 1);
        check("photo size reported", photo.getSizeBytes() > 0);
        check("observation reports its photo count",
                repo.observations().find(observation.getId()).getPhotoCount() == 1);
        check("no missing files reported", photos.listMissingFiles().isEmpty());

        photos.updateCaption(photo.getId(), "Story sequencing");
        check("caption updated", photos.find(photo.getId()).getCaption().equals("Story sequencing"));

        // A file deleted behind the app's back must be reported, not crash.
        Files.deleteIfExists(AppPaths.photoFile(photo.getFile()));
        check("missing file detected", photos.listMissingFiles().size() == 1);
        check("missing photo still listed", photos.find(photo.getId()) != null);

        photos.delete(photo.getId());
        check("photo row removed", photos.find(photo.getId()) == null);
        check("missing list empty again", photos.listMissingFiles().isEmpty());

        Photo forChild = photos.add(Photo.OwnerType.CHILD, child.getId(), child.getId(),
                source, "Profile picture");
        check("child-level photo stored", photos.listForChild(child.getId()).size() == 1);
        photos.deleteForChild(child.getId());
        check("child photos removed in bulk", photos.listForChild(child.getId()).isEmpty());
        check("photo file deleted with the row", !forChild.fileExists());
    }

    private static void testChildHistory(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        Child child = repo.children().listByGroup(group.getId(), false).get(0);

        List<HistoryEntry> history = repo.childHistory(child.getId(), null, null, null, null);
        check("history is not empty", !history.isEmpty());

        boolean hasAttendance = history.stream()
                .anyMatch(e -> e.kind() == HistoryEntry.Kind.ATTENDANCE);
        boolean hasObservation = history.stream()
                .anyMatch(e -> e.kind() == HistoryEntry.Kind.OBSERVATION);
        boolean hasInterview = history.stream()
                .anyMatch(e -> e.kind() == HistoryEntry.Kind.INTERVIEW);
        check("history merges attendance", hasAttendance);
        check("history merges observations", hasObservation);
        check("history merges interviews", hasInterview);

        check("history sorted newest first", isSortedNewestFirst(history));

        List<HistoryEntry> onlyObservations = repo.childHistory(child.getId(), null, null,
                List.of(HistoryEntry.Kind.OBSERVATION), null);
        check("history filters by record type", onlyObservations.stream()
                .allMatch(e -> e.kind() == HistoryEntry.Kind.OBSERVATION));

        List<HistoryEntry> rangeLimited = repo.childHistory(child.getId(),
                LocalDate.of(2026, 3, 5), null, null, null);
        check("history respects the date range", rangeLimited.stream()
                .allMatch(e -> e.date().compareTo("2026-03-05") >= 0));

        List<HistoryEntry> categoryFiltered = repo.childHistory(child.getId(), null, null, null, "social");
        check("history filters observations by category", categoryFiltered.stream()
                .filter(e -> e.kind() == HistoryEntry.Kind.OBSERVATION)
                .allMatch(e -> e.title().toLowerCase().contains("social")));

        Map<HistoryEntry.Kind, Integer> counts = repo.historyCounts(child.getId(), null, null);
        check("history counts add up", counts.values().stream().mapToInt(Integer::intValue).sum()
                == history.size());

        Repository.ChildSummary summary = repo.summaryFor(child.getId(), null, null);
        check("child summary reports attendance", summary.attendance().recorded() > 0);
        check("child summary reports observations", summary.observations() == 2);
        check("child summary reports interviews", summary.interviews() == 2);
    }

    private static boolean isSortedNewestFirst(List<HistoryEntry> entries) {
        for (int i = 1; i < entries.size(); i++) {
            String previous = entries.get(i - 1).date();
            String current = entries.get(i).date();
            if (previous.compareTo(current) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void testSearch(Repository repo) {
        List<SearchHit> communication = repo.search(
                Repository.SearchQuery.everything("communication"));
        check("keyword search finds the observation", !communication.isEmpty());
        check("search result carries the child", communication.get(0).childId() > 0);
        check("search result snippet is trimmed",
                communication.get(0).snippet().length() <= 200);

        List<SearchHit> interviews = repo.search(Repository.SearchQuery.everything("sleep routine"));
        check("keyword search reaches interview notes",
                interviews.stream().anyMatch(h -> h.kind() == HistoryEntry.Kind.INTERVIEW));

        List<SearchHit> plans = repo.search(Repository.SearchQuery.everything("picture cards"));
        check("keyword search reaches activity descriptions",
                plans.stream().anyMatch(h -> h.kind() == HistoryEntry.Kind.PLAN));

        List<SearchHit> byName = repo.search(Repository.SearchQuery.everything("Anna"));
        check("keyword search finds child profiles",
                byName.stream().anyMatch(h -> h.title().contains("profile")));

        List<SearchHit> noMatch = repo.search(Repository.SearchQuery.everything("zzzznotfound"));
        check("no matches returns an empty list", noMatch.isEmpty());

        Group group = repo.groups().findByName("Sunflowers");
        List<SearchHit> narrowed = repo.search(new Repository.SearchQuery("the", 0, group.getId(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "language", "", "", "",
                false, true, false, false, 100));
        check("scoped search respects class, dates and category", narrowed.stream()
                .allMatch(h -> h.kind() == HistoryEntry.Kind.OBSERVATION));

        check("snippet centres on the match",
                Repository.buildSnippet("a very long text about communication skills",
                        "communication").contains("communication"));
        check("snippet handles no match", !Repository.buildSnippet("some text", "absent").isEmpty());
    }

    private static void testReferentialIntegrity(Repository repo) {
        Group group = repo.groups().findByName("Sunflowers");
        Child child = repo.children().listByGroup(group.getId(), false).get(0);

        // A non-existent child must be rejected by the foreign key, not silently stored.
        boolean rejected = false;
        try {
            Observation orphan = new Observation();
            orphan.setChildId(999999);
            orphan.setDate("2026-03-02");
            orphan.setBody("should not be stored");
            repo.observations().insert(orphan);
        } catch (RuntimeException ex) {
            rejected = true;
        }
        check("observation for an unknown child is rejected", rejected);

        // Deleting a group must not delete its children.
        Group temporary = new Group();
        temporary.setName("Temporary group");
        repo.groups().insert(temporary);
        Child moved = newChild("Test", "Child", "2021-01-01", temporary.getId());
        repo.children().insert(moved);

        boolean blocked = !repo.groups().delete(temporary.getId());
        check("group with children cannot be deleted", blocked);

        repo.children().moveToGroup(moved.getId(), group.getId());
        check("group deleted once empty", repo.groups().delete(temporary.getId()));
        check("child survived the group deletion", repo.children().find(moved.getId()) != null);
        repo.children().delete(moved.getId());
    }

    private static void testDeletionCascade(Repository repo) throws IOException {
        Group group = repo.groups().findByName("Sunflowers");
        Child doomed = newChild("Delete", "Me", "2020-01-01", group.getId());
        repo.children().insert(doomed);

        Observation observation = new Observation();
        observation.setChildId(doomed.getId());
        observation.setGroupId(group.getId());
        observation.setDate(Dates.toIso(LocalDate.now()));
        observation.setBody("This record should disappear with the child.");
        repo.observations().insert(observation);

        Interview interview = new Interview();
        interview.setChildId(doomed.getId());
        interview.setDate(Dates.toIso(LocalDate.now()));
        interview.setTopics("Should disappear too");
        repo.interviews().insert(interview);

        Attendance attendance = new Attendance();
        attendance.setChildId(doomed.getId());
        attendance.setGroupId(group.getId());
        attendance.setDate(Dates.toIso(LocalDate.now()));
        attendance.setStatusEnum(Choices.AttendanceStatus.PRESENT);
        repo.attendance().save(attendance);

        Path source = Files.createTempFile("doomed", ".png");
        Files.write(source, minimalPng());
        Photo photo = repo.photos().add(Photo.OwnerType.OBSERVATION, observation.getId(),
                doomed.getId(), source, "goes away");
        String photoFile = photo.getFile();
        check("child has records before deletion",
                !repo.childHistory(doomed.getId(), null, null, null, null).isEmpty());

        repo.deleteChildCompletely(doomed.getId());

        check("child profile deleted", repo.children().find(doomed.getId()) == null);
        check("observations deleted with the child", repo.observations().find(observation.getId()) == null);
        check("interviews deleted with the child", repo.interviews().find(interview.getId()) == null);
        check("attendance deleted with the child",
                repo.attendance().find(doomed.getId(), Dates.toIso(LocalDate.now())) == null);
        check("photo file deleted with the child", !AppPaths.photoExists(photoFile));
        check("history is empty after deletion",
                repo.childHistory(doomed.getId(), null, null, null, null).isEmpty());
    }

    private static void testDatesAndText() {
        check("ISO round trip", Dates.toIso(Dates.fromIso("2026-03-02")).equals("2026-03-02"));
        check("display format", Dates.display("2026-03-02").equals("2 Mar 2026"));
        check("loose parsing accepts slashes",
                LocalDate.of(2026, 3, 2).equals(Dates.parseLoose("2/3/2026")));
        check("loose parsing rejects nonsense", Dates.parseLoose("not a date") == null);
        check("week starts on Monday",
                Dates.startOfWeek(LocalDate.of(2026, 3, 4)).equals(LocalDate.of(2026, 3, 2)));
        check("age text", Dates.ageText(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 4, 1))
                .equals("4 years 3 months"));
        check("relative date wording",
                Dates.relative(Dates.toIso(LocalDate.now())).equals("today"));

        check("blank detection", Text.isBlank("   ") && !Text.isBlank("x"));
        check("summary truncation", Text.summarise("a".repeat(400), 50).length() <= 51);
        check("initials", Text.initials("Anna Maria Berg").equals("AB"));
        check("html escaping", Text.escapeHtml("<b>&</b>").equals("&lt;b&gt;&amp;&lt;/b&gt;"));
        check("list splitting", Text.splitList("a, b; c").size() == 3);
        check("joining skips blanks", Text.joinNonBlank(" \u00b7 ", "a", "", "b").equals("a \u00b7 b"));
        check("plural wording", Text.plural(1, "child", "children").equals("1 child")
                && Text.plural(2, "child", "children").equals("2 children"));
    }

    // ------------------------------------------------------------------ helpers

    /** A 1x1 PNG, enough to exercise the copy-and-reference path. */
    private static byte[] minimalPng() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 'I', 'D', 'A', 'T',
                0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
                0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4,
                0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }

    /**
     * Checks the translation catalogues.
     *
     * <p>Every key the interface asks for must exist in English, because that is
     * the fallback. A partially translated language is allowed - untranslated
     * keys fall back to English rather than showing nothing - but the report says
     * how complete each one is, so a regression is visible.
     */
    private static void testTranslationCatalogues() {
        List<String> keys = Messages.keys();
        check("catalogue has keys (" + keys.size() + ")", keys.size() > 500);

        // English is the master list: no key may be missing from it.
        List<String> englishGaps = Messages.missingFor(Lang.ENGLISH);
        if (!englishGaps.isEmpty()) {
            System.out.println("        missing from English: " + englishGaps);
        }
        check("English defines every key", englishGaps.isEmpty());

        for (Lang lang : Lang.values()) {
            List<String> missing = Messages.missingFor(lang);
            int percent = (int) Math.round((keys.size() - missing.size()) * 100.0 / keys.size());
            System.out.println("        " + lang.englishName() + ": " + percent + "% translated"
                    + (missing.isEmpty() ? ""
                    : ", " + missing.size() + " key(s) fall back to English"));
        }

        // Mongolian ships as a complete translation; below 95% a screen would be
        // visibly half-English.
        List<String> mongolianGaps = Messages.missingFor(Lang.MONGOLIAN);
        check("Mongolian translation is at least 95% complete ("
                        + (keys.size() - mongolianGaps.size()) + "/" + keys.size() + ")",
                mongolianGaps.size() <= keys.size() * 5 / 100);
        if (!mongolianGaps.isEmpty()) {
            System.out.println("        still in English: " + mongolianGaps);
        }

        // Switching language must change the text that screens actually print.
        Lang original = Messages.current();
        try {
            Messages.setCurrent(Lang.ENGLISH);
            String englishToday = Dates.display(Dates.toIso(LocalDate.now()));
            String englishCategory = Choices.ObservationCategory.LANGUAGE.label();
            Messages.setCurrent(Lang.MONGOLIAN);
            String mongolianToday = Dates.display(Dates.toIso(LocalDate.now()));
            String mongolianCategory = Choices.ObservationCategory.LANGUAGE.label();
            check("switching language changes date text", !englishToday.equals(mongolianToday));
            check("switching language changes choice labels",
                    !englishCategory.equals(mongolianCategory));
            check("Mongolian date starts with the year", mongolianToday.startsWith("20"));
            check("Mongolian label is in Cyrillic",
                    mongolianCategory.matches(".*[\\u0400-\\u04FF].*"));
            check("Mongolian attendance short code translated",
                    !"P".equals(Choices.AttendanceStatus.PRESENT.shortCode()));
        } finally {
            Messages.setCurrent(original);
        }

        // A placeholder missing from a translation would print a stray "{0}".
        int placeholderMismatches = 0;
        for (String key : keys) {
            if (!Messages.has(Lang.MONGOLIAN, key)) {
                continue;
            }
            if (countPlaceholders(Messages.in(Lang.ENGLISH, key))
                    != countPlaceholders(Messages.in(Lang.MONGOLIAN, key))) {
                placeholderMismatches++;
                if (placeholderMismatches <= 5) {
                    System.out.println("        placeholder mismatch: " + key);
                }
            }
        }
        check("translations keep their placeholders (" + placeholderMismatches + " mismatched)",
                placeholderMismatches == 0);
    }

    /** How many distinct {n} placeholders a message contains. */
    private static int countPlaceholders(String message) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(message);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found.size();
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ok    " + name);
        } else {
            failed++;
            failures.add(name);
            System.out.println("  FAIL  " + name);
        }
    }
}
