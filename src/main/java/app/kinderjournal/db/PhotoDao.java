package app.kinderjournal.db;

import app.kinderjournal.model.Photo;
import app.kinderjournal.util.AppPaths;
import app.kinderjournal.util.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes photo attachments (the rows; the files live on disk). */
public class PhotoDao {

    private static final String SELECT_BASE = """
            SELECT id, owner_type, owner_id, child_id, file, caption, sort_order, added_at
            FROM photos
            """;

    private final Db db;

    public PhotoDao(Db db) {
        this.db = db;
    }

    private static Photo map(Row row) {
        Photo p = new Photo();
        p.setId(row.getLong("id"));
        p.setOwnerTypeId(row.getString("owner_type"));
        p.setOwnerId(row.getLong("owner_id"));
        p.setChildId(row.getLong("child_id"));
        p.setFile(row.getString("file"));
        p.setCaption(row.getString("caption"));
        p.setSortOrder(row.getInt("sort_order"));
        p.setAddedAt(row.getString("added_at"));
        Path file = p.path();
        p.setSizeBytes(file != null && Files.isRegularFile(file) ? fileSize(file) : -1);
        return p;
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return -1;
        }
    }

    // ------------------------------------------------------------------- queries

    /** Photos attached to one record, in display order. */
    public List<Photo> listFor(Photo.OwnerType ownerType, long ownerId) {
        return db.list(SELECT_BASE + "WHERE owner_type = ? AND owner_id = ? "
                + "ORDER BY sort_order ASC, id ASC", PhotoDao::map, ownerType.id(), ownerId);
    }

    /** Every photo belonging to a child, whatever record it is attached to. */
    public List<Photo> listForChild(long childId) {
        return db.list(SELECT_BASE + "WHERE child_id = ? ORDER BY added_at DESC, id DESC",
                PhotoDao::map, childId);
    }

    /** Photos attached directly to observations of a child (used by reports). */
    public List<Photo> listForChildByOwnerType(long childId, Photo.OwnerType ownerType) {
        return db.list(SELECT_BASE + "WHERE child_id = ? AND owner_type = ? "
                + "ORDER BY added_at ASC, id ASC", PhotoDao::map, childId, ownerType.id());
    }

    public Photo find(long id) {
        return db.one(SELECT_BASE + "WHERE id = ?", PhotoDao::map, id);
    }

    public int countFor(Photo.OwnerType ownerType, long ownerId) {
        return (int) db.count("SELECT COUNT(*) AS n FROM photos WHERE owner_type = ? AND owner_id = ?",
                ownerType.id(), ownerId);
    }

    public int countForChild(long childId) {
        return (int) db.count("SELECT COUNT(*) AS n FROM photos WHERE child_id = ?", childId);
    }

    /**
     * Photo rows whose image file has gone missing.
     *
     * <p>Files can disappear if a teacher tidies the data folder by hand or a
     * backup is restored without the photos. The UI lists these so the problem is
     * visible instead of showing a broken thumbnail.
     */
    public List<Photo> listMissingFiles() {
        List<Photo> out = new ArrayList<>();
        for (Photo p : db.list(SELECT_BASE + "ORDER BY added_at DESC", PhotoDao::map)) {
            if (!AppPaths.photoExists(p.getFile())) {
                out.add(p);
            }
        }
        return out;
    }

    // -------------------------------------------------------------------- writes

    /**
     * Copies a file into the local photos folder and records it.
     *
     * <p>The image is copied (never referenced in place), so the attachment keeps
     * working if the teacher later moves or deletes the original.
     */
    public Photo add(Photo.OwnerType ownerType, long ownerId, long childId, Path source, String caption)
            throws IOException {
        String stored = AppPaths.storePhoto(source);
        Photo p = new Photo();
        p.setOwnerType(ownerType);
        p.setOwnerId(ownerId);
        p.setChildId(childId);
        p.setFile(stored);
        p.setCaption(Text.clean(caption));
        p.setSortOrder(countFor(ownerType, ownerId));
        p.setAddedAt(Db.now());
        p.setId(insert(p));
        // Fill in the size straight away so callers see the same values a
        // re-read from the database would produce.
        Path file = p.path();
        p.setSizeBytes(file != null && Files.isRegularFile(file) ? fileSize(file) : -1);
        return p;
    }

    public long insert(Photo p) {
        return db.insert("""
                INSERT INTO photos(owner_type, owner_id, child_id, file, caption, sort_order, added_at)
                VALUES(?, ?, ?, ?, ?, ?, ?)""",
                p.getOwnerTypeId(), p.getOwnerId(), p.getChildId() == 0 ? null : p.getChildId(),
                p.getFile(), p.getCaption(), p.getSortOrder(),
                Text.isBlank(p.getAddedAt()) ? Db.now() : p.getAddedAt());
    }

    public void updateCaption(long id, String caption) {
        db.run(() -> db.update("UPDATE photos SET caption = ? WHERE id = ?", Text.clean(caption), id));
    }

    /** Removes the row and deletes the image file from disk. */
    public void delete(long id) {
        Photo p = find(id);
        if (p == null) {
            return;
        }
        db.run(() -> db.update("DELETE FROM photos WHERE id = ?", id));
        deleteFileQuietly(p.getFile());
    }

    /** Removes every photo attached to a record (used when that record is deleted). */
    public void deleteFor(Photo.OwnerType ownerType, long ownerId) {
        List<Photo> photos = listFor(ownerType, ownerId);
        db.run(() -> db.update("DELETE FROM photos WHERE owner_type = ? AND owner_id = ?",
                ownerType.id(), ownerId));
        for (Photo p : photos) {
            deleteFileQuietly(p.getFile());
        }
    }

    /** Removes every photo of a child, e.g. when the child's profile is deleted. */
    public void deleteForChild(long childId) {
        List<Photo> photos = listForChild(childId);
        db.run(() -> db.update("DELETE FROM photos WHERE child_id = ?", childId));
        for (Photo p : photos) {
            deleteFileQuietly(p.getFile());
        }
    }

    /** Records that a file is deliberately shared and must not be deleted. */
    public boolean fileInUse(String storedFile) {
        return db.count("SELECT COUNT(*) AS n FROM photos WHERE file = ?", storedFile) > 0
                || db.count("SELECT COUNT(*) AS n FROM children WHERE photo_file = ?", storedFile) > 0;
    }

    /** Re-orders photos within one record. */
    public void reorder(List<Long> orderedIds) {
        db.transaction(() -> {
            for (int i = 0; i < orderedIds.size(); i++) {
                db.update("UPDATE photos SET sort_order = ? WHERE id = ?", i, orderedIds.get(i));
            }
            return null;
        });
    }

    /** Stops the file being orphaned when the child's profile photo is replaced. */
    private static void deleteFileQuietly(String storedFile) {
        if (Text.isBlank(storedFile)) {
            return;
        }
        Path file = AppPaths.photoFile(storedFile);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The database row is gone; a leftover file is harmless and reported
            // by listMissingFiles() only in the opposite direction.
        }
    }
}
