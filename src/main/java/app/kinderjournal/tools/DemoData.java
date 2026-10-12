package app.kinderjournal.tools;

import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Attendance;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Choices;
import app.kinderjournal.model.Group;
import app.kinderjournal.model.Interview;
import app.kinderjournal.model.LessonPlan;
import app.kinderjournal.model.Observation;
import app.kinderjournal.model.Photo;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Dates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Fills a data folder with a realistic set of records.
 *
 * <p>Used to exercise the interface with a term's worth of content - several
 * children, a month of attendance, observations across every category, interviews
 * with follow-ups, a planned week and a few photos - so layout and printing can be
 * checked against something that looks like real use rather than an empty database.
 */
public final class DemoData {

    private DemoData() {
    }

    /** Creates the sample records, returning how many rows were written. */
    public static int seed(Path dataDir) {
        System.setProperty("kinderjournal.dataDir", dataDir.toAbsolutePath().toString());
        int written = 0;
        try (Repository repo = Repository.open()) {
            Group sunflowers = group(repo, "Sunflower group", "4\u20135", "Room 2", "2025/2026", 1);
            Group acorns = group(repo, "Acorn group", "3\u20134", "Room 1", "2025/2026", 2);
            written += 2;
            // Start on the group that has the term's records. The classes are
            // ordered by sort_order and then by name, so without this the sample
            // data opened on the nearly empty group and looked unpopulated.
            repo.db().saveSetting("last_group_id", String.valueOf(sunflowers.getId()));

            List<Child> children = List.of(
                    child(repo, sunflowers, "Anna", "Berg", "2020-03-12", "Maria Berg", "mother",
                            "555-0101", "Peanuts"),
                    child(repo, sunflowers, "Ben", "Olsen", "2021-07-01", "Jonas Olsen", "father",
                            "555-0102", ""),
                    child(repo, sunflowers, "Carla", "Meyer", "2020-11-20", "Sofia Meyer", "mother",
                            "555-0103", "Bee stings"),
                    child(repo, sunflowers, "Davit", "Erdene", "2021-01-05", "Sarnai Erdene", "mother",
                            "555-0104", ""),
                    child(repo, sunflowers, "Elif", "Kaya", "2020-09-15", "Ahmet Kaya", "father",
                            "555-0105", "Lactose"),
                    child(repo, acorns, "Frida", "Larsen", "2021-10-02", "Ingrid Larsen", "mother",
                            "555-0106", ""));
            written += children.size();

            LocalDate today = LocalDate.now();
            LocalDate termStart = today.minusDays(45);

            // Attendance for the last six weeks of weekdays.
            for (LocalDate day = termStart; !day.isAfter(today); day = day.plusDays(1)) {
                if (day.getDayOfWeek().getValue() >= 6) {
                    continue;
                }
                int index = 0;
                for (Child child : children) {
                    if (child.getGroupId() != sunflowers.getId()) {
                        continue;
                    }
                    Attendance attendance = new Attendance();
                    attendance.setChildId(child.getId());
                    attendance.setGroupId(sunflowers.getId());
                    attendance.setDate(Dates.toIso(day));
                    // A believable pattern: mostly present, occasional absence.
                    int pattern = (int) ((day.toEpochDay() + index * 7) % 17);
                    if (pattern == 3) {
                        attendance.setStatusEnum(Choices.AttendanceStatus.ABSENT);
                        attendance.setReason(index % 2 == 0 ? "unwell" : "family appointment");
                    } else if (pattern == 8) {
                        attendance.setStatusEnum(Choices.AttendanceStatus.LATE);
                        attendance.setArrivalTime("09:15");
                        attendance.setReason("late bus");
                    } else if (pattern == 12) {
                        attendance.setStatusEnum(Choices.AttendanceStatus.EXCUSED);
                        attendance.setReason("family trip");
                    } else {
                        attendance.setStatusEnum(Choices.AttendanceStatus.PRESENT);
                        attendance.setArrivalTime("08:30");
                        attendance.setDepartureTime("13:00");
                    }
                    attendance.setNote("");
                    repo.attendance().save(attendance);
                    written++;
                    index++;
                }
            }

            // Observations spread across the categories.
            String[][] notes = {
                    {"language", "New words at circle time",
                            "Used three new words during show and tell and asked a follow-up question "
                                    + "about the story. Confident communication in front of the group.",
                            "morning circle"},
                    {"social", "Invited a friend to play",
                            "Asked another child to join the block corner and shared the pieces "
                                    + "without being prompted.", "free play"},
                    {"physical", "Climbing with control",
                            "Climbed the frame to the top rung and came down carefully, checking "
                                    + "footing on each step.", "outdoor play"},
                    {"emotional", "Settled quickly after goodbye",
                            "Was sad when mum left, then joined the puzzle table within a few minutes "
                                    + "and stayed settled.", "arrival"},
                    {"cognitive", "Sorted by two attributes",
                            "Sorted the buttons by colour and then by size, and explained the rule "
                                    + "out loud.", "maths table"},
                    {"participation", "Helped set the table",
                            "Carried the cups to the snack table and counted them out for each chair.",
                            "snack time"},
                    {"achievement", "First full sentence in English",
                            "Said \"I want the red one please\" without help.", "art table"},
                    {"support", "Needs help with scissors",
                            "Found cutting along a line difficult; will practise with thicker paper.",
                            "art table"},
                    {"behaviour", "Restless after lunch",
                            "Found it hard to sit through the story; settled once given a job to do.",
                            "afternoon story"},
            };
            int childIndex = 0;
            for (String[] note : notes) {
                Child child = children.get(childIndex % 4);
                Observation observation = new Observation();
                observation.setChildId(child.getId());
                observation.setGroupId(sunflowers.getId());
                observation.setDate(Dates.toIso(termStart.plusDays(4L * childIndex + 3)));
                observation.setCategory(note[0]);
                observation.setTitle(note[1]);
                observation.setBody(note[2]);
                observation.setContext(note[3]);
                observation.setAuthor("Teacher");
                repo.observations().insert(observation);
                written++;
                childIndex++;
            }

            // Two interviews, one with an outstanding follow-up.
            Interview parentInterview = new Interview();
            parentInterview.setChildId(children.get(0).getId());
            parentInterview.setGroupId(sunflowers.getId());
            parentInterview.setDate(Dates.toIso(today.minusDays(10)));
            parentInterview.setTypeEnum(Choices.InterviewType.PARENT);
            parentInterview.setModeEnum(Choices.InterviewMode.IN_PERSON);
            parentInterview.setParticipants("Maria Berg (mother), class teacher");
            parentInterview.setTopics("Settling in, sleep routine, friendships");
            parentInterview.setDiscussed("Maria says Anna is tired after lunch but enjoys coming. "
                    + "She asked whether we could send home a short note about the week's activities.");
            parentInterview.setConclusions("No concerns about the transition. Tiredness is normal for "
                    + "the first term.");
            parentInterview.setFollowUp("Start sending the weekly activity note home on Fridays");
            parentInterview.setFollowUpDate(Dates.toIso(today.minusDays(2)));
            repo.interviews().insert(parentInterview);
            written++;

            Interview childInterview = new Interview();
            childInterview.setChildId(children.get(1).getId());
            childInterview.setGroupId(sunflowers.getId());
            childInterview.setDate(Dates.toIso(today.minusDays(5)));
            childInterview.setTypeEnum(Choices.InterviewType.CHILD);
            childInterview.setModeEnum(Choices.InterviewMode.IN_PERSON);
            childInterview.setParticipants("Ben");
            childInterview.setTopics("Favourite activities");
            childInterview.setDiscussed("Ben talked about building a garage for the toy cars and "
                    + "wanted more long blocks.");
            childInterview.setConclusions("Block play is his strongest interest at the moment.");
            childInterview.setFollowUp("Add more long blocks to the construction area");
            childInterview.setFollowUpDone(true);
            repo.interviews().insert(childInterview);
            written++;

            // A planned week for the sunflower group.
            LocalDate monday = Dates.startOfWeek(today);
            written += plan(repo, sunflowers, "Morning circle and news", monday, "09:00", "09:30",
                    "routine", "Settle in and share news",
                    "Each child says one thing about yesterday.", "Name cards",
                    Choices.PlanStatus.COMPLETED, "The group was talkative; keep to two minutes each.");
            written += plan(repo, sunflowers, "Story sequencing", monday, "09:30", "10:15",
                    "language", "Retell a story in the right order",
                    "Read \"The Enormous Turnip\", order the picture cards, retell in pairs.",
                    "Picture cards, felt board", Choices.PlanStatus.COMPLETED,
                    "Children enjoyed the card ordering; next time use two stories.");
            written += plan(repo, sunflowers, "Counting to ten", monday.plusDays(1), "09:30", "10:15",
                    "maths", "Count objects accurately to ten",
                    "Count buttons into pots, then match the numeral card.", "Buttons, numeral cards",
                    Choices.PlanStatus.PLANNED, "");
            written += plan(repo, sunflowers, "Leaf printing", monday.plusDays(2), "10:00", "11:00",
                    "art", "Explore texture and colour",
                    "Collect leaves outside, then print them with poster paint.",
                    "Leaves, poster paint, paper", Choices.PlanStatus.PLANNED, "");
            written += plan(repo, sunflowers, "Spring project", monday.plusDays(3), "11:00", "11:45",
                    "science", "Notice how plants change over time",
                    "Plant bean seeds and start a growing diary.", "Beans, pots, soil, diary sheets",
                    Choices.PlanStatus.PLANNED, "");
            LessonPlan range = new LessonPlan();
            range.setGroupId(sunflowers.getId());
            range.setTitle("Spring project (all week)");
            range.setDate(Dates.toIso(monday));
            range.setEndDate(Dates.toIso(monday.plusDays(4)));
            range.setAreaEnum(Choices.PlanArea.THEME);
            range.setObjectives("Follow a project across the week and record what changes.");
            range.setDescription("Daily short session on the growing beans, ending with a display.");
            range.setMaterials("Beans, pots, soil, camera");
            range.setStatusEnum(Choices.PlanStatus.PLANNED);
            repo.plans().insert(range);
            written++;

            // A reusable template.
            LessonPlan template = new LessonPlan();
            template.setGroupId(sunflowers.getId());
            template.setTitle("Outdoor obstacle course");
            template.setDate(Dates.toIso(monday));
            template.setAreaEnum(Choices.PlanArea.MOTOR);
            template.setObjectives("Practise balancing, jumping and crawling.");
            template.setDescription("Set out four stations and rotate the group around them.");
            template.setMaterials("Cones, bench, mats, hoops");
            template.setSequence("1. Warm up\n2. Station 1 balance\n3. Station 2 jump\n"
                    + "4. Station 3 crawl\n5. Station 4 throw\n6. Cool down");
            template.setTemplate(true);
            repo.plans().insert(template);
            written++;

            // A couple of photos on the first child's observation, and a profile
            // picture for the child: without one the avatar falls back to initials,
            // so the circular crop would never be exercised by the sample data.
            List<Observation> observations = repo.observations()
                    .listForChild(children.get(0).getId(), null, null, null);
            if (!observations.isEmpty()) {
                Path image = writeSampleImage(dataDir);
                try {
                    repo.photos().add(Photo.OwnerType.OBSERVATION, observations.get(0).getId(),
                            children.get(0).getId(), image, "Ordering the story cards");
                    Photo profile = repo.photos().add(Photo.OwnerType.CHILD,
                            children.get(0).getId(), children.get(0).getId(), image,
                            "Profile picture");
                    children.get(0).setPhotoFile(profile.getFile());
                    repo.children().update(children.get(0));
                    written += 2;
                } catch (IOException ex) {
                    throw new IllegalStateException("Could not attach the sample photos", ex);
                }
            }

            repo.db().saveSetting("teacher_name", "Ms Teacher");
        }
        return written;
    }

    private static Group group(Repository repo, String name, String ages, String room, String year,
                               int order) {
        Group existing = repo.groups().findByName(name);
        if (existing != null) {
            return existing;
        }
        Group group = new Group();
        group.setName(name);
        group.setAgeRange(ages);
        group.setRoom(room);
        group.setSchoolYear(year);
        group.setTeacher("Ms Teacher");
        group.setSortOrder(order);
        group.setDescription("A sample class created for trying the application out.");
        repo.groups().insert(group);
        return group;
    }

    /**
     * Creates a sample child.
     *
     * <p>Idempotent, like {@link #group}: the sample data is seeded by hand on a
     * folder that may already hold it, and re-running must not produce a second
     * copy of every child.
     */
    private static Child child(Repository repo, Group group, String first, String last, String birth,
                               String guardian, String relation, String phone, String allergies) {
        for (Child existing : repo.children().listAll()) {
            if (existing.getFirstName().equals(first) && existing.getLastName().equals(last)
                    && existing.getGroupId() == group.getId()) {
                return existing;
            }
        }
        Child child = new Child();
        child.setFirstName(first);
        child.setLastName(last);
        child.setBirthDate(birth);
        child.setGender("");
        child.setGroupId(group.getId());
        child.setStartDate(Dates.toIso(LocalDate.now().minusMonths(8)));
        child.setGuardianName(guardian);
        child.setGuardianRelation(relation);
        child.setGuardianPhone(phone);
        child.setGuardianEmail(guardian.toLowerCase().replace(' ', '.') + "@example.com");
        child.setAddress("12 Example Street");
        child.setAllergies(allergies);
        child.setNotes(allergies.isBlank() ? "" : "Allergy plan on file.");
        repo.children().insert(child);
        return child;
    }

    private static int plan(Repository repo, Group group, String title, LocalDate date,
                            String start, String end, String area, String objectives,
                            String description, String materials, Choices.PlanStatus status,
                            String reflection) {
        LessonPlan plan = new LessonPlan();
        plan.setGroupId(group.getId());
        plan.setTitle(title);
        plan.setDate(Dates.toIso(date));
        plan.setStartTime(start);
        plan.setEndTime(end);
        plan.setArea(area);
        plan.setObjectives(objectives);
        plan.setDescription(description);
        plan.setMaterials(materials);
        plan.setStatusEnum(status);
        plan.setReflection(reflection);
        repo.plans().insert(plan);
        return 1;
    }

    /** Writes a small PNG into the data folder so photo features can be exercised. */
    private static Path writeSampleImage(Path dataDir) {
        Path staging = dataDir.resolve("sample-photo.png");
        try {
            Files.createDirectories(dataDir);
            Files.write(staging, SAMPLE_PNG);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write the sample image", ex);
        }
        return staging;
    }

    /**
     * A 64x64 PNG of a simple figure, so the photo features can be exercised
     * without a file to hand.
     *
     * <p>Square on purpose. The earlier sample was 30x20, a shape no camera
     * produces, and it hid a real fault: a picture whose shape differs from its
     * frame was being scaled to fit rather than cropped to fill, so a wide image
     * sat in a mostly empty circle or tile. A test image of the wrong shape is
     * worth keeping for exactly that reason - {@code tools} can write a wide one
     * by hand when the crop needs checking.
     */
    private static final byte[] SAMPLE_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABxElEQVR42u1bu27DMAyMDgnQ"
            + "sUu/s2OWjl0y9ju7ZCyQwZ0KFIYd63FHyjK12pDujhQtkXSapul05IHTwUcIcHQBzpaLvV7f"
            + "swPO/faVLDAlZRAsIewliEQAJnG1EFQBlMRVQlAEsCTOFgJ7Js9YH3smz8BRtQV6Ic7YEhiJ"
            + "fA0+jES+BmfcBUazfilejEi+BDdGJZ+LP2LAyNbP4XH2APT9eVt99vZxNcWyehJUWP8ZcQsh"
            + "lk6J6MHqjPepMYBt/VoybBGWeKE3y1t7AnombyFCnANU+59tNdZ8c37hASFACBAChAAhgGCw"
            + "LzOqW2J4gHJyltWUOQK5B7SCVydIYNGIUEvCIiliFgNKyVilxkxzgn+kesoJuiRFl4SwJv50"
            + "C1h0aM29wCIH6JoUzSFrlQjNEkDlBVskVSKs8UEvBREvT0AP3ZqebTNRGLFoSPQsjGzhh7oZ"
            + "0bMwkoMbyo5Mz8JILl70XhVSxwQoVFWBzZ23xFvBdi119N6av3SrNnWLz8tMlgeY+eWpNlCD"
            + "9YmxPsf/X6/lK0X5X+Dx+HFrprpcXlJkhT0F8LQ+Y33smTwDB/ZOvhUPRiDfggujkK/Fl47+"
            + "+/wv83oYAq7z3voAAAAASUVORK5CYII=");

}
