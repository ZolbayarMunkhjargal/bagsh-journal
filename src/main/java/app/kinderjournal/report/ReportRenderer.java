package app.kinderjournal.report;

import app.kinderjournal.db.AttendanceDao;
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
import app.kinderjournal.i18n.Messages;
import app.kinderjournal.model.SearchHit;
import app.kinderjournal.ui.Styles;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns stored records into a print-ready HTML document.
 *
 * <p>HTML is used because it gives long reports that break cleanly across pages,
 * repeating table headers, and page numbers through the browser's own print
 * dialog - including "Microsoft Print to PDF", which is how a report is saved as
 * a PDF without any extra software. Images are embedded as data URIs so a report
 * is a single self-contained file that still shows its photos if it is copied
 * elsewhere.
 *
 * <p>The renderer only lays out what the teacher entered. It never adds its own
 * conclusions about a child, so nothing reaches a parent that the teacher has not
 * read and approved.
 */
public final class ReportRenderer {

    /** A generated document plus the metadata needed to save or print it. */
    public record Rendered(String title, String html) {

        public boolean isEmpty() {
            return html == null || html.isBlank();
        }
    }

    private final Repository repo;

    public ReportRenderer(Repository repo) {
        this.repo = repo;
    }

    // ------------------------------------------------------------------ entry

    /** Builds the document for the given report type and options. */
    public Rendered render(ReportOptions options) {
        String title = options.type().label();
        String body = switch (options.type()) {
            case ATTENDANCE_DAY -> renderAttendanceDay(options);
            case ATTENDANCE_SUMMARY -> renderAttendanceSummary(options);
            case ATTENDANCE_MONTH -> renderAttendanceMonth(options);
            case CHILD_PROFILE -> renderChildProfile(options);
            case CHILD_OBSERVATIONS -> renderChildObservations(options);
            case CHILD_HISTORY -> renderChildHistory(options);
            case INTERVIEW_SUMMARY -> renderInterviewSummary(options);
            case DAILY_PLAN -> renderPlan(options, false);
            case WEEKLY_PLAN -> renderPlan(options, true);
            case CLASS_PERIOD -> renderClassPeriod(options);
            case SEARCH_RESULTS -> renderSearchResults(options);
        };
        return new Rendered(title, wrap(title, options, body));
    }

    /** A file name that is safe on Windows and tells the teacher what it holds. */
    public static String suggestedFileName(ReportOptions options, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(title));
        if (options.childId() > 0) {
            sb.append('-').append(safe(String.valueOf(options.childId())));
        }
        if (options.from() != null) {
            sb.append('-').append(Dates.ISO.format(options.from()));
            if (options.to() != null && !options.to().equals(options.from())) {
                sb.append("-to-").append(Dates.ISO.format(options.to()));
            }
        }
        return sb.append(".html").toString();
    }

    private static String safe(String value) {
        return Text.clean(value).replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    // ------------------------------------------------------------------ document

    /** Wraps a body in the shared page frame: heading, page setup and footer. */
    private String wrap(String title, ReportOptions options, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"").append(Messages.current().code()).append("\">\n<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<title>").append(Text.escapeHtml(title)).append("</title>\n");
        sb.append("<style>\n").append(Styles.reportCss()).append('\n');
        // Page setup lives here so the browser's print dialog picks up the right
        // paper size, margins and page numbers without the teacher configuring it.
        sb.append("@page { size: A4 ").append(options.landscape() ? "landscape" : "portrait")
                .append("; margin: 15mm 14mm 16mm 14mm; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<div class=\"report-header\">");
        sb.append("<h1>").append(Text.escapeHtml(title)).append("</h1>");
        sb.append("<div class=\"subtitle\">").append(Text.escapeHtml(subtitleFor(options)))
                .append("</div>");
        sb.append("</div>\n");

        if (!Text.isBlank(options.notes())) {
            sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.notes")))
                    .append("</h2>\n");
            sb.append("<div class=\"note-block\">").append(Text.escapeHtml(options.notes()))
                    .append("</div>\n");
        }

        sb.append(body);

        sb.append("<div class=\"footer-note\">");
        sb.append(Text.escapeHtml(Messages.get("report.footer.generated",
                Dates.display(Dates.toIso(LocalDate.now())))));
        if (!Text.isBlank(options.teacherName())) {
            sb.append(' ').append(Text.escapeHtml(Messages.get("report.footer.by",
                    options.teacherName()))).append('.');
        }
        sb.append(' ').append(Text.escapeHtml(Messages.get("report.footer.privacy")));
        sb.append("</div>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private String subtitleFor(ReportOptions options) {
        List<String> parts = new ArrayList<>();
        Child child = options.childId() > 0 ? repo.children().find(options.childId()) : null;
        Group group = options.groupId() > 0 ? repo.groups().find(options.groupId()) : null;

        if (child != null) {
            // For a child report, name the child and the class they are actually in;
            // the requested class may be a different one, chosen for a class report.
            parts.add(child.fullName());
            String childGroup = child.getGroupName();
            if (Text.isBlank(childGroup) && group != null) {
                childGroup = group.getName();
            }
            if (!Text.isBlank(childGroup)) {
                parts.add(childGroup);
            }
            if (group != null && !Text.isBlank(group.getTeacher())) {
                parts.add(Messages.in(Messages.current(), "classForm.teacher") + ": "
                        + group.getTeacher());
            }
        } else if (group != null) {
            parts.add(group.getName());
            if (!Text.isBlank(group.getTeacher())) {
                parts.add(Messages.in(Messages.current(), "classForm.teacher") + ": "
                        + group.getTeacher());
            }
        }

        if (options.from() != null || options.to() != null) {
            String fromText = options.from() == null
                    ? Messages.in(Messages.current(), "report.rangeStart")
                    : Dates.display(options.from());
            String toText = options.to() == null
                    ? Dates.display(LocalDate.now())
                    : Dates.display(options.to());
            parts.add(fromText + " \u2013 " + toText);
        }
        if (!Text.isBlank(options.keyword())) {
            parts.add(Messages.get("reports.searchKeyword") + ": \"" + options.keyword() + "\"");
        }
        return String.join("  \u00b7  ", parts);
    }

    // ------------------------------------------------------------- attendance

    private String renderAttendanceDay(ReportOptions options) {
        Group group = repo.groups().find(options.groupId());
        LocalDate date = options.from() == null ? LocalDate.now() : options.from();
        if (group == null) {
            return emptyNotice(Messages.get("report.empty.chooseClass"));
        }
        List<Repository.RegisterRow> register = repo.register(group.getId(), date);
        if (register.isEmpty()) {
            return emptyNotice(Messages.get("report.empty.noChildren", group.getName()));
        }

        AttendanceDao.Summary summary = repo.attendance().summaryForGroup(group.getId(), date, date);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.register",
                        Dates.displayLong(Dates.toIso(date)))))
                .append("</h2>\n");

        sb.append("<table>\n<thead><tr>")
                .append("<th style=\"width:8mm\">").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "attendance.column.number"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "attendance.column.child"))).append("</th>")
                .append("<th style=\"width:26mm\">").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "attendance.column.mark"))).append("</th>")
                .append("<th style=\"width:32mm\">").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "history.arrival"))).append("</th>")
                .append("<th style=\"width:32mm\">").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "history.departure"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(
                        Messages.in(Messages.current(), "attendance.column.reason"))).append("</th>")
                .append("</tr></thead>\n<tbody>\n");
        int index = 1;
        for (Repository.RegisterRow row : register) {
            Choices.AttendanceStatus status = row.recorded() ? row.status()
                    : Choices.AttendanceStatus.PRESENT;
            sb.append("<tr><td>").append(index++).append("</td>");
            sb.append("<td>").append(Text.escapeHtml(row.child().fullName())).append("</td>");
            sb.append("<td class=\"status-").append(status.id()).append("\">")
                    .append(Text.escapeHtml(row.recorded() ? status.label() : "\u2013"))
                    .append("</td>");
            sb.append("<td>").append(Text.escapeHtml(row.attendance().getArrivalTime())).append("</td>");
            sb.append("<td>").append(Text.escapeHtml(row.attendance().getDepartureTime())).append("</td>");
            sb.append("<td>").append(Text.escapeHtml(Text.joinNonBlank(" \u00b7 ",
                    row.attendance().getReason(), row.attendance().getNote()))).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");

        sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.totals")))
                .append("</h2>\n");
        sb.append("<div class=\"summary-grid\">");
        sb.append(summaryBoxHtml(Messages.in(Messages.current(), "attendance.present"),
                String.valueOf(summary.present())));
        sb.append(summaryBoxHtml(Messages.in(Messages.current(), "attendance.absent"),
                String.valueOf(summary.absent())));
        sb.append(summaryBoxHtml(Messages.in(Messages.current(), "attendance.late"),
                String.valueOf(summary.late())));
        sb.append(summaryBoxHtml(Messages.in(Messages.current(), "attendance.excused"),
                String.valueOf(summary.excused())));
        sb.append(summaryBoxHtml(Messages.in(Messages.current(), "attendance.attending"),
                summary.rateText()));
        sb.append("</div>\n");

        sb.append(signatureBlock(group));
        return sb.toString();
    }

    private String renderAttendanceSummary(ReportOptions options) {
        Group group = options.groupId() > 0 ? repo.groups().find(options.groupId()) : null;
        if (group == null) {
            return emptyNotice("Choose a class to produce an attendance summary.");
        }
        LocalDate to = options.to() == null ? LocalDate.now() : options.to();
        LocalDate from = options.from() == null ? to.minusMonths(1) : options.from();

        List<Child> children = repo.children().listByGroup(group.getId(), false);
        if (children.isEmpty()) {
            return emptyNotice(Messages.get("report.empty.noChildren", group.getName()));
        }
        Map<Long, AttendanceDao.Summary> perChild =
                repo.attendance().summariesByChild(children, from, to);
        AttendanceDao.Summary total = repo.attendance().summaryForGroup(group.getId(), from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.attendanceFrom",
                        Dates.display(from), Dates.display(to))))
                .append("</h2>\n");

        sb.append("<table>\n<thead><tr>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.column.child")))
                .append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.present"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.late"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.absent"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.excused"))).append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.column.recorded")))
                .append("</th>")
                .append("<th>").append(Text.escapeHtml(withKey("attendance.attending")))
                .append("</th>")
                .append("</tr></thead>\n<tbody>\n");
        for (Child child : children) {
            AttendanceDao.Summary summary = perChild.getOrDefault(child.getId(),
                    AttendanceDao.Summary.EMPTY);
            sb.append("<tr><td>").append(Text.escapeHtml(child.fullName())).append("</td>")
                    .append("<td>").append(summary.present()).append("</td>")
                    .append("<td>").append(summary.late()).append("</td>")
                    .append("<td>").append(summary.absent()).append("</td>")
                    .append("<td>").append(summary.excused()).append("</td>")
                    .append("<td>").append(summary.recorded()).append("</td>")
                    .append("<td>").append(summary.rateText()).append("</td></tr>\n");
        }
        sb.append("<tr><td><strong>Class total</strong></td>")
                .append("<td>").append(total.present()).append("</td>")
                .append("<td>").append(total.late()).append("</td>")
                .append("<td>").append(total.absent()).append("</td>")
                .append("<td>").append(total.excused()).append("</td>")
                .append("<td>").append(total.recorded()).append("</td>")
                .append("<td>").append(total.rateText()).append("</td></tr>\n");
        sb.append("</tbody></table>\n");

        sb.append("<p class=\"meta-line\">")
                .append(Text.escapeHtml(withKey("report.blankDay")))
                .append("</p>\n");
        sb.append(signatureBlock(group));
        return sb.toString();
    }

    private String renderAttendanceMonth(ReportOptions options) {
        Group group = options.groupId() > 0 ? repo.groups().find(options.groupId()) : null;
        if (group == null) {
            return emptyNotice(Messages.get("report.empty.chooseClassGrid"));
        }
        LocalDate anchor = options.from() == null ? LocalDate.now() : options.from();
        java.time.YearMonth month = java.time.YearMonth.from(anchor);
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();

        List<Child> children = repo.children().listByGroup(group.getId(), false);
        if (children.isEmpty()) {
            return emptyNotice(Messages.get("report.empty.noChildren", group.getName()));
        }
        Map<Long, Map<String, Attendance>> marks = new LinkedHashMap<>();
        for (Child child : children) {
            Map<String, Attendance> byDate = new LinkedHashMap<>();
            for (Attendance record : repo.attendance().listForChild(child.getId(), first, last)) {
                byDate.put(record.getDate(), record);
            }
            marks.put(child.getId(), byDate);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(
                        Dates.monthName(month.getYear(), month.getMonthValue())))
                .append("</h2>\n");
        sb.append("<p class=\"meta-line\">")
                .append(Text.escapeHtml(withKey("report.marksLegend")))
                .append("</p>\n");

        sb.append("<table class=\"grid\">\n<thead><tr><th>Child</th>");
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            sb.append("<th>").append(day).append("</th>");
        }
        sb.append("<th>Present</th><th>Absent</th></tr></thead>\n<tbody>\n");

        for (Child child : children) {
            sb.append("<tr><td class=\"name\">").append(Text.escapeHtml(child.fullName()))
                    .append("</td>");
            int present = 0;
            int absent = 0;
            Map<String, Attendance> byDate = marks.get(child.getId());
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                String iso = Dates.toIso(month.atDay(day));
                Attendance record = byDate.get(iso);
                if (record == null) {
                    sb.append("<td class=\"blank\">&middot;</td>");
                    continue;
                }
                Choices.AttendanceStatus status = record.statusEnum();
                if (status.countedAsAttending()) {
                    present++;
                } else if (status == Choices.AttendanceStatus.ABSENT) {
                    absent++;
                }
                sb.append("<td class=\"status-").append(status.id()).append("\">")
                        .append(status.shortCode()).append("</td>");
            }
            sb.append("<td>").append(present).append("</td><td>").append(absent).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append(signatureBlock(group));
        return sb.toString();
    }

    // ------------------------------------------------------------------ child

    private String renderChildProfile(ReportOptions options) {
        Child child = repo.children().find(options.childId());
        if (child == null) {
            return emptyNotice(Messages.get("report.empty.chooseChildProfile"));
        }
        StringBuilder sb = new StringBuilder();

        sb.append("<h2>").append(Text.escapeHtml(withKey("report.heading.profile")))
                .append("</h2>\n<div class=\"summary-grid\">");
        if (options.includePhotos()) {
            String photo = photoTag(child.getPhotoFile(), 34);
            if (photo != null) {
                sb.append(photo);
            }
        }
        sb.append("<table style=\"max-width:150mm\">\n<tbody>\n");
        profileRow(sb, Messages.in(Messages.current(), "childForm.firstName"), child.fullName());
        profileRow(sb, Messages.in(Messages.current(), "children.field.dateOfBirth"), child.getBirthDate() == null ? ""
                : Dates.display(child.getBirthDate()) + "  (" + child.ageText() + ")");
        profileRow(sb, Messages.in(Messages.current(), "common.class"), child.getGroupName());
        profileRow(sb, Messages.in(Messages.current(), "children.field.firstDay"), child.getStartDate() == null ? ""
                : Dates.display(child.getStartDate()));
        profileRow(sb, Messages.in(Messages.current(), "children.field.guardian"), Text.joinNonBlank(" \u00b7 ",
                child.getGuardianName(), child.getGuardianRelation()));
        profileRow(sb, Messages.in(Messages.current(), "children.field.phone"), child.getGuardianPhone());
        profileRow(sb, Messages.in(Messages.current(), "children.field.email"), child.getGuardianEmail());
        profileRow(sb, Messages.in(Messages.current(), "children.field.secondContact"), child.getSecondContact());
        profileRow(sb, Messages.in(Messages.current(), "children.field.address"), child.getAddress());
        profileRow(sb, Messages.in(Messages.current(), "children.field.allergies"), child.getAllergies());
        profileRow(sb, Messages.in(Messages.current(), "children.field.profileNotes"), child.getNotes());
        sb.append("</tbody></table>\n</div>\n");

        if (options.includeAttendanceSummary()) {
            LocalDate to = options.to() == null ? LocalDate.now() : options.to();
            LocalDate from = options.from() == null ? to.minusMonths(3) : options.from();
            AttendanceDao.Summary summary = repo.attendance().summaryForChild(child.getId(), from, to);
            sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.attendanceFrom",
                            Dates.display(from), Dates.display(to))))
                    .append("</h2>\n<div class=\"summary-grid\">");
            sb.append(summaryBoxHtml("Present", String.valueOf(summary.present())));
            sb.append(summaryBoxHtml("Late", String.valueOf(summary.late())));
            sb.append(summaryBoxHtml("Absent", String.valueOf(summary.absent())));
            sb.append(summaryBoxHtml("Excused", String.valueOf(summary.excused())));
            sb.append(summaryBoxHtml("Attending", summary.rateText()));
            sb.append("</div>\n");
        }

        if (options.includePhotos()) {
            String gallery = photoGallery(repo.photos().listForChild(child.getId()), 4);
            if (!gallery.isEmpty()) {
                sb.append("<h2>").append(Text.escapeHtml(withKey("report.heading.photos"))).append("</h2>\n").append(gallery);
            }
        }
        sb.append(signatureBlock(null));
        return sb.toString();
    }

    private String renderChildObservations(ReportOptions options) {
        Child child = repo.children().find(options.childId());
        if (child == null) {
            return emptyNotice(Messages.get("report.empty.chooseChildObservations"));
        }
        List<Observation> observations = repo.observations().listForChild(child.getId(),
                options.from(), options.to(), Text.isBlank(options.category()) ? null : options.category());

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(child.fullName())).append("</h2>\n");
        if (observations.isEmpty()) {
            return sb + emptyNotice(Messages.get("report.empty.noObservations"));
        }

        // Grouped by developmental area, which is how a progress discussion is held.
        Map<String, List<Observation>> byCategory = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation.isConfidential() && !options.includeConfidential()) {
                continue;
            }
            byCategory.computeIfAbsent(observation.getCategory(), key -> new ArrayList<>())
                    .add(observation);
        }

        sb.append("<p class=\"meta-line\">")
                .append(Text.escapeHtml(Messages.get("report.observationsBetween",
                        observations.size(), Dates.display(options.from()),
                        Dates.display(options.to()))))
                .append("</p>\n");

        for (Choices.ObservationCategory category : Choices.ObservationCategory.values()) {
            List<Observation> group = byCategory.get(category.id());
            if (group == null || group.isEmpty()) {
                continue;
            }
            sb.append("<h2>").append(Text.escapeHtml(category.label()))
                    .append("  (").append(group.size()).append(")</h2>\n");
            for (Observation observation : group) {
                sb.append(entryBlock(observation.getDate(), category.label(),
                        observation.getContext(),
                        Text.joinNonBlank(" \u00b7 ", observation.getTitle(),
                                observation.getContext()),
                        observation.getBody(),
                        options.includePhotos() ? photoGallery(
                                repo.photos().listFor(Photo.OwnerType.OBSERVATION,
                                        observation.getId()), 3) : ""));
            }
        }
        sb.append(signatureBlock(null));
        return sb.toString();
    }

    private String renderChildHistory(ReportOptions options) {
        Child child = repo.children().find(options.childId());
        if (child == null) {
            return emptyNotice(Messages.get("report.empty.chooseChildHistory"));
        }
        List<HistoryEntry> entries = repo.childHistory(child.getId(), options.from(), options.to(),
                options.kinds(), null);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(child.fullName())).append("</h2>\n");
        if (options.includeProfile()) {
            sb.append("<p class=\"meta-line\">")
                    .append(Text.escapeHtml(Text.joinNonBlank("  \u00b7  ",
                            child.getGroupName(), child.birthSummary(),
                            child.getStartDate() == null ? ""
                                    : Messages.get("children.startedOn", Dates.display(child.getStartDate())))))
                    .append("</p>\n");
        }
        if (entries.isEmpty()) {
            return sb + emptyNotice(Messages.get("report.empty.noHistory"));
        }

        sb.append("<p class=\"meta-line\">").append(entries.size())
                .append(" entries between ")
                .append(Text.escapeHtml(Dates.display(Dates.toIso(options.from()))))
                .append(" and ")
                .append(Text.escapeHtml(Dates.display(Dates.toIso(options.to()))))
                .append(".</p>\n");

        String currentMonth = "";
        for (HistoryEntry entry : entries) {
            String month = entry.date() == null || entry.date().length() < 7
                    ? "" : entry.date().substring(0, 7);
            if (!month.equals(currentMonth)) {
                currentMonth = month;
                if (!month.isEmpty()) {
                    java.time.YearMonth yearMonth = java.time.YearMonth.parse(month);
                    sb.append("<h2>").append(Text.escapeHtml(
                                    Dates.monthName(yearMonth.getYear(), yearMonth.getMonthValue())))
                            .append("</h2>\n");
                }
            }
            String detail = detailFor(entry, options);
            sb.append(entryBlock(entry.date(), entry.kind().label(), "",
                    Text.joinNonBlank(" \u00b7 ", entry.title(), entry.summary()),
                    detail, ""));
        }
        sb.append(signatureBlock(null));
        return sb.toString();
    }

    /** Pulls the full text of a history entry so the report is not just summaries. */
    private String detailFor(HistoryEntry entry, ReportOptions options) {
        StringBuilder sb = new StringBuilder();
        switch (entry.kind()) {
            case OBSERVATION -> {
                Observation observation = repo.observations().find(entry.recordId());
                if (observation == null) {
                    return "";
                }
                if (observation.isConfidential() && !options.includeConfidential()) {
                    return "";
                }
                sb.append(observation.getBody());
                if (options.includePhotos()) {
                    sb.append(photoGallery(repo.photos().listFor(Photo.OwnerType.OBSERVATION,
                            observation.getId()), 3));
                }
            }
            case INTERVIEW -> {
                Interview interview = repo.interviews().find(entry.recordId());
                if (interview == null) {
                    return "";
                }
                appendSection(sb, Messages.in(Messages.current(), "interviews.field.participants"), interview.getParticipants());
                appendSection(sb, Messages.in(Messages.current(), "interviews.field.topics"), interview.getTopics());
                appendSection(sb, Messages.in(Messages.current(), "interviews.field.discussed"), interview.getDiscussed());
                appendSection(sb, Messages.in(Messages.current(), "interviews.field.conclusions"), interview.getConclusions());
                if (!Text.isBlank(interview.getFollowUp())) {
                    String followUpText = interview.getFollowUp()
                            + (Text.isBlank(interview.getFollowUpDate()) ? ""
                            : " " + Messages.get("report.followUpDue",
                            Dates.display(interview.getFollowUpDate())))
                            + " " + Messages.get(interview.isFollowUpDone()
                            ? "report.followUpCompleted" : "report.followUpOutstanding");
                    appendSection(sb, Messages.in(Messages.current(), "interviews.field.followUp"),
                            followUpText);
                }
                if (options.includePhotos()) {
                    sb.append(photoGallery(repo.photos().listFor(Photo.OwnerType.INTERVIEW,
                            interview.getId()), 3));
                }
            }
            case ATTENDANCE -> {
                Child child = repo.children().find(options.childId());
                LocalDate date = Dates.fromIso(entry.date());
                if (child == null || date == null) {
                    return "";
                }
                Attendance record = repo.attendanceFor(child.getId(), date);
                if (record == null) {
                    return "";
                }
                sb.append(Text.escapeHtml(withKey("history.status"))).append(": ")
                        .append(Text.escapeHtml(record.statusEnum().label()));
                appendSection(sb, Messages.in(Messages.current(), "common.reason"), record.getReason());
                appendSection(sb, Messages.in(Messages.current(), "common.note"), record.getNote());
            }
            case PLAN -> {
                LessonPlan plan = repo.plans().find(entry.recordId());
                if (plan == null) {
                    return "";
                }
                appendSection(sb, Messages.in(Messages.current(), "plans.section.objectives"), plan.getObjectives());
                appendSection(sb, Messages.in(Messages.current(), "plans.section.activities"), plan.getDescription());
                appendSection(sb, Messages.in(Messages.current(), "plans.section.materials"), plan.getMaterials());
                if (!Text.isBlank(plan.getReflection())) {
                    appendSection(sb, Messages.in(Messages.current(), "plans.section.reflection"), plan.getReflection());
                }
            }
            default -> sb.append(Text.escapeHtml(entry.detail()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- interviews

    private String renderInterviewSummary(ReportOptions options) {
        List<Interview> interviews;
        Child child = options.childId() > 0 ? repo.children().find(options.childId()) : null;
        if (child != null) {
            interviews = repo.interviews().listForChild(child.getId(), options.from(), options.to(), null);
        } else {
            Group group = repo.groups().find(options.groupId());
            if (group == null) {
                return emptyNotice("Choose a class or a child to produce an interview summary.");
            }
            interviews = repo.interviews().listForGroup(group.getId(), options.from(), options.to(), null);
        }
        if (interviews.isEmpty()) {
            return emptyNotice(Messages.get("report.empty.noInterviews"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(interviews.size())
                .append(interviews.size() == 1 ? " interview" : " interviews")
                .append("</h2>\n");
        for (Interview interview : interviews) {
            StringBuilder detail = new StringBuilder();
            appendSection(detail, Messages.in(Messages.current(), "interviews.field.participants"), interview.getParticipants());
            appendSection(detail, Messages.in(Messages.current(), "interviews.field.topics"), interview.getTopics());
            appendSection(detail, Messages.in(Messages.current(), "interviews.field.discussed"), interview.getDiscussed());
            appendSection(detail, Messages.in(Messages.current(), "interviews.field.conclusions"), interview.getConclusions());
            if (!Text.isBlank(interview.getFollowUp())) {
                String followUpText = interview.getFollowUp()
                        + (Text.isBlank(interview.getFollowUpDate()) ? ""
                        : " " + Messages.get("report.followUpDue",
                        Dates.display(interview.getFollowUpDate())))
                        + " " + Messages.get(interview.isFollowUpDone()
                        ? "report.followUpCompleted" : "report.followUpOutstanding");
                appendSection(detail, Messages.in(Messages.current(), "interviews.field.followUp"),
                        followUpText);
            }
            sb.append(entryBlock(interview.getDate(),
                    interview.typeEnum().label(),
                    interview.getChildName(),
                    interview.modeEnum().label(),
                    detail.toString(),
                    options.includePhotos()
                            ? photoGallery(repo.photos().listFor(Photo.OwnerType.INTERVIEW,
                            interview.getId()), 3)
                            : ""));
        }

        long outstanding = interviews.stream()
                .filter(i -> !i.isFollowUpDone() && !Text.isBlank(i.getFollowUp())).count();
        if (outstanding > 0) {
            sb.append("<h2>").append(Text.escapeHtml(withKey("report.heading.outstanding"))).append("</h2>\n<ul>\n");
            for (Interview interview : interviews) {
                if (interview.isFollowUpDone() || Text.isBlank(interview.getFollowUp())) {
                    continue;
                }
                sb.append("<li>").append(Text.escapeHtml(interview.getChildName())).append(": ")
                        .append(Text.escapeHtml(interview.getFollowUp()))
                        .append(Text.isBlank(interview.getFollowUpDate()) ? ""
                                : " " + Text.escapeHtml(Messages.get("report.followUpDue",
                                Dates.display(interview.getFollowUpDate()))))
                        .append("</li>\n");
            }
            sb.append("</ul>\n");
        }
        sb.append(signatureBlock(null));
        return sb.toString();
    }

    // ------------------------------------------------------------------ plans

    private String renderPlan(ReportOptions options, boolean weekly) {
        Group group = options.groupId() > 0 ? repo.groups().find(options.groupId()) : null;
        LocalDate anchor = options.from() == null ? LocalDate.now() : options.from();

        StringBuilder sb = new StringBuilder();
        if (weekly) {
            LocalDate start = Dates.startOfWeek(anchor);
            LocalDate end = start.plusDays(6);
            Map<String, List<LessonPlan>> byDay = repo.plans().byDay(options.groupId(), start, end);

            sb.append("<h2>").append(Text.escapeHtml(Messages.get("report.heading.weekOf",
                            Dates.display(start), Dates.display(end))))
                    .append("</h2>\n");
            boolean any = byDay.values().stream().anyMatch(list -> !list.isEmpty());
            if (!any) {
                return sb + emptyNotice(Messages.get("report.empty.noWeekPlan"));
            }
            sb.append("<table>\n<thead><tr>");
            for (int i = 0; i < 7; i++) {
                LocalDate day = start.plusDays(i);
                sb.append("<th>").append(Text.escapeHtml(Dates.weekdayShort(day)))
                        .append("<br>").append(Text.escapeHtml(Dates.display(Dates.toIso(day))))
                        .append("</th>");
            }
            sb.append("</tr></thead>\n<tbody><tr>");
            for (int i = 0; i < 7; i++) {
                LocalDate day = start.plusDays(i);
                sb.append("<td>");
                List<LessonPlan> plans = byDay.getOrDefault(Dates.toIso(day), List.of());
                if (plans.isEmpty()) {
                    sb.append("<span class=\"meta-line\">&ndash;</span>");
                }
                for (LessonPlan plan : plans) {
                    sb.append("<div class=\"entry\" style=\"border-left-color:#0f766e\">");
                    sb.append("<strong>").append(Text.escapeHtml(plan.getTitle())).append("</strong>");
                    if (!plan.timeLabel().isEmpty()) {
                        sb.append("<div class=\"meta-line\">")
                                .append(Text.escapeHtml(plan.timeLabel())).append("</div>");
                    }
                    sb.append("<div class=\"meta-line\">")
                            .append(Text.escapeHtml(plan.areaEnum().label())).append(" \u00b7 ")
                            .append(Text.escapeHtml(plan.statusEnum().label())).append("</div>");
                    if (!Text.isBlank(plan.getObjectives())) {
                        sb.append("<div>").append(Text.escapeHtml(plan.getObjectives())).append("</div>");
                    }
                    if (!Text.isBlank(plan.getMaterials())) {
                        sb.append("<div class=\"meta-line\">Materials: ")
                                .append(Text.escapeHtml(plan.getMaterials())).append("</div>");
                    }
                    sb.append("</div>");
                }
                sb.append("</td>");
            }
            sb.append("</tr></tbody></table>\n");
        } else {
            List<LessonPlan> plans = repo.plans().listForDay(options.groupId(), anchor);
            sb.append("<h2>").append(Text.escapeHtml(Dates.displayLong(Dates.toIso(anchor))))
                    .append("</h2>\n");
            if (plans.isEmpty()) {
                return sb + emptyNotice(Messages.get("report.empty.noDayPlan"));
            }
            for (LessonPlan plan : plans) {
                StringBuilder detail = new StringBuilder();
                appendSection(detail, Messages.in(Messages.current(), "plans.field.area"), plan.areaEnum().label());
                appendSection(detail, Messages.in(Messages.current(), "common.time"),
                        plan.timeLabel());
                appendSection(detail, Messages.in(Messages.current(), "common.status"), plan.statusEnum().label());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.objectives"), plan.getObjectives());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.activities"), plan.getDescription());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.sequence"), plan.getSequence());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.materials"), plan.getMaterials());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.instructions"), plan.getInstructions());
                appendSection(detail, Messages.in(Messages.current(), "plans.section.reflection"), plan.getReflection());
                sb.append(entryBlock(plan.getDate(), plan.areaEnum().label(),
                        plan.getGroupName(), plan.getTitle(), detail.toString(), ""));
            }
        }
        if (group == null && options.groupId() == 0) {
            sb.append("<p class=\"meta-line\">This plan covers every class.</p>\n");
        }
        sb.append(signatureBlock(group));
        return sb.toString();
    }

    // ------------------------------------------------------------ class period

    private String renderClassPeriod(ReportOptions options) {
        Group group = repo.groups().find(options.groupId());
        if (group == null) {
            return emptyNotice(Messages.get("report.empty.chooseClass"));
        }
        LocalDate to = options.to() == null ? LocalDate.now() : options.to();
        LocalDate from = options.from() == null ? to.minusMonths(1) : options.from();
        List<Child> children = repo.children().listByGroup(group.getId(), false);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(group.getName())).append("</h2>\n");
        sb.append("<p class=\"meta-line\">")
                .append(Text.escapeHtml(Dates.display(from) + " \u2013 " + Dates.display(to)))
                .append("  \u00b7  ")
                .append(Text.escapeHtml(Messages.get("count.children", children.size())))
                .append("</p>\n");

        if (options.includeAttendanceSummary()) {
            Map<Long, AttendanceDao.Summary> perChild =
                    repo.attendance().summariesByChild(children, from, to);
            AttendanceDao.Summary total = repo.attendance().summaryForGroup(group.getId(), from, to);
            sb.append("<h3>").append(Text.escapeHtml(withKey("report.heading.attendance"))).append("</h3>\n<table>\n<thead><tr><th>").append(Text.escapeHtml(withKey("attendance.column.child"))).append("</th><th>").append(Text.escapeHtml(withKey("attendance.present"))).append("</th>"
                    + "<th>").append(Text.escapeHtml(withKey("attendance.late"))).append("</th><th>").append(Text.escapeHtml(withKey("attendance.absent"))).append("</th><th>").append(Text.escapeHtml(withKey("attendance.excused"))).append("</th><th>").append(Text.escapeHtml(withKey("attendance.attending"))).append("</th></tr></thead>\n"
                    + "<tbody>\n");
            for (Child child : children) {
                AttendanceDao.Summary summary = perChild.getOrDefault(child.getId(),
                        AttendanceDao.Summary.EMPTY);
                sb.append("<tr><td>").append(Text.escapeHtml(child.fullName())).append("</td>")
                        .append("<td>").append(summary.present()).append("</td>")
                        .append("<td>").append(summary.late()).append("</td>")
                        .append("<td>").append(summary.absent()).append("</td>")
                        .append("<td>").append(summary.excused()).append("</td>")
                        .append("<td>").append(summary.rateText()).append("</td></tr>\n");
            }
            sb.append("<tr><td><strong>Class</strong></td><td>").append(total.present())
                    .append("</td><td>").append(total.late()).append("</td><td>")
                    .append(total.absent()).append("</td><td>").append(total.excused())
                    .append("</td><td>").append(total.rateText()).append("</td></tr>\n");
            sb.append("</tbody></table>\n");
        }

        if (options.includeObservations()) {
            List<Observation> observations = repo.observations()
                    .listForChildren(children, from, to, null, 0);
            sb.append("<h3>").append(Text.escapeHtml(withKey("report.heading.observations"))).append("  (").append(observations.size()).append(")</h3>\n");
            if (observations.isEmpty()) {
                sb.append("<p class=\"meta-line\">None recorded in this period.</p>\n");
            }
            for (Observation observation : observations) {
                if (observation.isConfidential() && !options.includeConfidential()) {
                    continue;
                }
                sb.append(entryBlock(observation.getDate(), observation.categoryEnum().label(),
                        observation.getChildName(),
                        Text.joinNonBlank(" \u00b7 ", observation.getTitle(), observation.getContext()),
                        observation.getBody(), ""));
            }
        }

        if (options.includeInterviews()) {
            List<Interview> interviews = repo.interviews().listForChildren(children, from, to, null, 0);
            sb.append("<h3>").append(Text.escapeHtml(withKey("report.heading.interviews"))).append("  (").append(interviews.size()).append(")</h3>\n");
            if (interviews.isEmpty()) {
                sb.append("<p class=\"meta-line\">None recorded in this period.</p>\n");
            }
            for (Interview interview : interviews) {
                StringBuilder detail = new StringBuilder();
                appendSection(detail, Messages.in(Messages.current(), "interviews.field.topics"), interview.getTopics());
                appendSection(detail, Messages.in(Messages.current(), "interviews.field.discussed"), interview.getDiscussed());
                appendSection(detail, Messages.in(Messages.current(), "interviews.field.conclusions"), interview.getConclusions());
                appendSection(detail, Messages.in(Messages.current(), "interviews.field.followUp"), interview.getFollowUp());
                sb.append(entryBlock(interview.getDate(), interview.typeEnum().label(),
                        interview.getChildName(), interview.modeEnum().label(),
                        detail.toString(), ""));
            }
        }

        if (options.includePlans()) {
            List<LessonPlan> plans = repo.plans().listBetween(group.getId(), from, to);
            sb.append("<h3>").append(Text.escapeHtml(withKey("report.heading.activities"))).append("  (").append(plans.size()).append(")</h3>\n");
            if (plans.isEmpty()) {
                sb.append("<p class=\"meta-line\">None planned in this period.</p>\n");
            }
            for (LessonPlan plan : plans) {
                sb.append(entryBlock(plan.getDate(), plan.areaEnum().label(),
                        plan.statusEnum().label(), plan.getTitle(),
                        Text.joinNonBlank("\n", plan.getObjectives(), plan.getDescription()), ""));
            }
        }

        sb.append(signatureBlock(group));
        return sb.toString();
    }

    // ---------------------------------------------------------------- search

    private String renderSearchResults(ReportOptions options) {
        Repository.SearchQuery query = new Repository.SearchQuery(options.keyword(),
                options.childId(), options.groupId(), options.from(), options.to(),
                options.category(), "", "", "", true, true, true, true, 500);
        List<SearchHit> hits = repo.search(query);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Text.escapeHtml(withKey("report.heading.searchResults"))).append("</h2>\n");
        sb.append("<p class=\"meta-line\">Searching for \"")
                .append(Text.escapeHtml(options.keyword())).append("\" returned ")
                .append(hits.size()).append(hits.size() == 1 ? " record" : " records")
                .append(".</p>\n");
        if (hits.isEmpty()) {
            return sb + emptyNotice(Messages.get("report.empty.noSearchResults"));
        }
        sb.append("<table>\n<thead><tr><th style=\"width:22mm\">Date</th>"
                + "<th style=\"width:26mm\">Type</th><th style=\"width:34mm\">Child</th>"
                + "<th>Record</th></tr></thead>\n<tbody>\n");
        for (SearchHit hit : hits) {
            sb.append("<tr><td>").append(Text.escapeHtml(Dates.display(hit.date()))).append("</td>")
                    .append("<td>").append(Text.escapeHtml(hit.kind().label())).append("</td>")
                    .append("<td>").append(Text.escapeHtml(hit.childName())).append("</td>")
                    .append("<td><strong>").append(Text.escapeHtml(hit.title()))
                    .append("</strong><br>").append(Text.escapeHtml(hit.snippet()))
                    .append("</td></tr>\n");
        }
        sb.append("</tbody></table>\n");
        return sb.toString();
    }

    // ----------------------------------------------------------------- pieces

    private String entryBlock(String isoDate, String kind, String who, String title, String body,
                              String extraHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"entry\">");
        sb.append("<div><span class=\"entry-date\">")
                .append(Text.escapeHtml(Dates.display(isoDate))).append("</span>");
        if (!Text.isBlank(kind)) {
            sb.append(" <span class=\"entry-kind\">")
                    .append(Text.escapeHtml(kind)).append("</span>");
        }
        if (!Text.isBlank(who)) {
            sb.append(" <span class=\"meta-line\">\u00b7 ")
                    .append(Text.escapeHtml(who)).append("</span>");
        }
        sb.append("</div>");
        if (!Text.isBlank(title)) {
            sb.append("<div><strong>").append(Text.escapeHtml(title)).append("</strong></div>");
        }
        if (!Text.isBlank(body)) {
            sb.append("<div class=\"note-block\">").append(Text.escapeHtml(body)).append("</div>");
        }
        if (!Text.isBlank(extraHtml)) {
            sb.append(extraHtml);
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String label, String value) {
        if (Text.isBlank(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append(": ").append(value.trim());
    }

    private void profileRow(StringBuilder sb, String label, String value) {
        if (Text.isBlank(value)) {
            return;
        }
        sb.append("<tr><th style=\"width:52mm\">").append(Text.escapeHtml(label))
                .append("</th><td>").append(Text.escapeHtml(value)).append("</td></tr>\n");
    }

    private String summaryBoxHtml(String label, String value) {
        return "<div class=\"summary-box\"><div class=\"value\">" + Text.escapeHtml(value)
                + "</div><div class=\"label\">" + Text.escapeHtml(label) + "</div></div>";
    }

    private String signatureBlock(Group group) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"signature\">");
        sb.append(Text.escapeHtml(Messages.get("report.teacherSignature")))
                .append(" ______________________________");
        if (group != null && !group.getTeacher().isBlank()) {
            sb.append("&nbsp;&nbsp;&nbsp;").append(Text.escapeHtml(group.getTeacher()));
        }
        sb.append("<br>").append(Text.escapeHtml(Messages.get("report.dateLine")))
                .append(" __________________");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String emptyNotice(String message) {
        return "<p class=\"meta-line\">" + Text.escapeHtml(message) + "</p>\n";
    }

    /** Shorthand: the current language's text for a key. */
    private static String withKey(String key) {
        return Messages.in(Messages.current(), key);
    }

    // ------------------------------------------------------------------ photos

    /**
     * One photo, embedded as a data URI.
     *
     * <p>Embedding means the saved HTML file is self-contained, so a report still
     * shows its pictures after being copied to a memory stick or emailed.
     */
    private String photoTag(String storedFile, double widthMm) {
        if (Text.isBlank(storedFile) || !AppPaths.photoExists(storedFile)) {
            return null;
        }
        Path file = AppPaths.photoFile(storedFile);
        String mime = mimeType(file);
        if (mime == null) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > 8 * 1024 * 1024) {
                return null;
            }
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return "<img src=\"data:" + mime + ";base64," + encoded + "\" style=\"width:"
                    + (int) widthMm + "mm\">";
        } catch (IOException ex) {
            return null;
        }
    }

    private String photoGallery(List<Photo> photos, int perRow) {
        if (photos == null || photos.isEmpty()) {
            return "";
        }
        double width = perRow <= 3 ? 46 : 34;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"photos\">\n");
        for (Photo photo : photos) {
            String tag = photoTag(photo.getFile(), width);
            if (tag == null) {
                continue;
            }
            sb.append("<div class=\"photo\" style=\"width:").append((int) width + 2)
                    .append("mm\">").append(tag);
            if (!Text.isBlank(photo.getCaption())) {
                sb.append("<div class=\"caption\">")
                        .append(Text.escapeHtml(photo.getCaption())).append("</div>");
            }
            sb.append("</div>\n");
        }
        sb.append("</div>\n");
        return sb.length() <= "<div class=\"photos\">\n</div>\n".length() ? "" : sb.toString();
    }

    private static String mimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    /** Counts what a report will contain, so the teacher can confirm before printing. */
    public Map<String, Integer> preview(ReportOptions options) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Child child = options.childId() > 0 ? repo.children().find(options.childId()) : null;
        if (child != null) {
            Repository.ChildSummary summary = repo.summaryFor(child.getId(),
                    options.from(), options.to());
            counts.put(Messages.in(Messages.current(), "classes.activity.attendanceRecords"), summary.attendance().recorded());
            counts.put(Messages.in(Messages.current(), "nav.observations"), summary.observations());
            counts.put(Messages.in(Messages.current(), "nav.interviews"), summary.interviews());
            counts.put("Photos", summary.photos());
        } else {
            List<Child> children = options.groupId() > 0
                    ? repo.children().listByGroup(options.groupId(), false) : List.of();
            counts.put(Messages.in(Messages.current(), "common.children"), children.size());
            counts.put(Messages.in(Messages.current(), "classes.activity.attendanceRecords"), repo.attendance()
                    .listForGroup(options.groupId(), options.from(), options.to()).size());
            counts.put(Messages.in(Messages.current(), "nav.observations"), repo.observations()
                    .listForGroup(options.groupId(), options.from(), options.to(), null).size());
            counts.put(Messages.in(Messages.current(), "nav.interviews"), repo.interviews()
                    .listForGroup(options.groupId(), options.from(), options.to(), null).size());
            counts.put(Messages.in(Messages.current(), "report.heading.activities"), repo.plans()
                    .listBetween(options.groupId(), options.from(), options.to()).size());
        }
        return counts;
    }
}
