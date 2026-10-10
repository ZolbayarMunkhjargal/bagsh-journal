package app.kinderjournal.model;

import app.kinderjournal.util.Text;

/**
 * A single hit from the advanced search.
 *
 * <p>Carries where the match came from so the teacher can jump straight to it,
 * plus a snippet with the matched words highlighted.
 */
public record SearchHit(
        String date,
        HistoryEntry.Kind kind,
        long recordId,
        long childId,
        long groupId,
        String childName,
        String groupName,
        String title,
        String snippet) {

    /** A compact "12 Mar 2026 \u00b7 Observation \u00b7 Anna Berg" meta line. */
    public String metaLine() {
        return Text.joinNonBlank(" \u00b7 ",
                app.kinderjournal.util.Dates.display(date),
                kind.label(),
                childName,
                groupName);
    }

    /** True when this hit is about a specific child (so it can be opened). */
    public boolean hasChild() {
        return childId > 0;
    }
}
