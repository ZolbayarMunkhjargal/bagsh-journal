package app.kinderjournal.ui;

import app.kinderjournal.db.Repository;
import app.kinderjournal.model.Child;
import app.kinderjournal.model.Group;
import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared state for the running application: the data repository, which class and
 * child are currently selected, and who to tell when that changes.
 *
 * <p>A single context object is passed to every screen, which is what lets the
 * "current class" chosen on the dashboard follow the teacher into attendance,
 * lesson planning and reports without re-picking it each time.
 */
public final class AppContext {

    private final Repository repository;
    private final List<Consumer<Group>> groupListeners = new ArrayList<>();
    private final List<Runnable> dataListeners = new ArrayList<>();

    private Group currentGroup;
    private Child currentChild;
    private LocalDate selectedDate = LocalDate.now();
    private String teacherName;

    public AppContext(Repository repository) {
        this.repository = repository;
        this.teacherName = repository.db().setting("teacher_name", "");
        this.currentGroup = repository.groups().firstActive();
    }

    public Repository repo() {
        return repository;
    }

    // -------------------------------------------------------------- current class

    public Group currentGroup() {
        return currentGroup;
    }

    /** The current class id, or 0 when none is selected. */
    public long currentGroupId() {
        return currentGroup == null ? 0 : currentGroup.getId();
    }

    public String currentGroupName() {
        return currentGroup == null ? "No class selected" : currentGroup.getName();
    }

    public void setCurrentGroup(Group group) {
        if (group == null && currentGroup == null) {
            return;
        }
        if (group != null && currentGroup != null && group.getId() == currentGroup.getId()) {
            this.currentGroup = group;
            return;
        }
        this.currentGroup = group;
        repository.db().saveSetting("last_group_id",
                group == null ? "" : String.valueOf(group.getId()));
        for (Consumer<Group> listener : new ArrayList<>(groupListeners)) {
            listener.accept(group);
        }
    }

    /** Re-reads classes and keeps the selection pointing at a valid one. */
    public void reloadCurrentGroup() {
        List<Group> groups = repository.groups().listActive();
        if (groups.isEmpty()) {
            setCurrentGroup(null);
            return;
        }
        long wanted = currentGroupId();
        for (Group g : groups) {
            if (g.getId() == wanted) {
                setCurrentGroup(g);
                return;
            }
        }
        setCurrentGroup(groups.get(0));
    }

    /** Called when the teacher switches class; screens refresh their contents. */
    public void onGroupChanged(Consumer<Group> listener) {
        groupListeners.add(listener);
    }

    // ------------------------------------------------------------------ current child

    public Child currentChild() {
        return currentChild;
    }

    public void setCurrentChild(Child child) {
        this.currentChild = child;
    }

    public long currentChildId() {
        return currentChild == null ? 0 : currentChild.getId();
    }

    // ------------------------------------------------------------------- date

    public LocalDate selectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date == null ? LocalDate.now() : date;
    }

    // ------------------------------------------------------------------- teacher

    public String teacherName() {
        return teacherName;
    }

    public void setTeacherName(String name) {
        this.teacherName = Text.clean(name);
        repository.db().saveSetting("teacher_name", this.teacherName);
    }

    // ------------------------------------------------------------------- refresh

    /**
     * Tells every interested screen that records changed, so an edit made in one
     * module is reflected in the others without a restart.
     */
    public void notifyDataChanged() {
        for (Runnable listener : new ArrayList<>(dataListeners)) {
            listener.run();
        }
    }

    public void onDataChanged(Runnable listener) {
        dataListeners.add(listener);
    }

    /** A short "Friday, 12 September 2026" label for headers. */
    public String selectedDateLabel() {
        return Dates.displayLong(Dates.toIso(selectedDate));
    }
}
