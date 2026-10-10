package app.kinderjournal.model;

import app.kinderjournal.util.Text;

/**
 * A local photo attachment.
 *
 * <p>Only the file name is stored in the database; the image itself lives in the
 * {@code photos} folder of the data directory. That keeps the database small and
 * lets a backup copy the two together.
 */
public class Photo {

    /** What the photo is attached to. */
    public enum OwnerType {
        CHILD("child"),
        OBSERVATION("observation"),
        INTERVIEW("interview"),
        PLAN("plan"),
        GROUP("group");

        private final String id;

        OwnerType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** The label in the language currently selected. */
        public String label() {
            return app.kinderjournal.i18n.Messages.get("choice.owner." + id);
        }

        public static OwnerType fromId(String id) {
            for (OwnerType t : values()) {
                if (t.id.equalsIgnoreCase(id)) {
                    return t;
                }
            }
            return CHILD;
        }
    }

    private long id;
    private OwnerType ownerType = OwnerType.CHILD;
    private long ownerId;
    private long childId;            // denormalised so a child's photos are easy to list
    private String file = "";        // file name inside the photos folder
    private String caption = "";
    private int sortOrder;
    private String addedAt = "";
    private long sizeBytes = -1;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(OwnerType ownerType) {
        this.ownerType = ownerType == null ? OwnerType.CHILD : ownerType;
    }

    public void setOwnerTypeId(String id) {
        this.ownerType = OwnerType.fromId(id);
    }

    public String getOwnerTypeId() {
        return ownerType.id();
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    public long getChildId() {
        return childId;
    }

    public void setChildId(long childId) {
        this.childId = childId;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file == null ? "" : file;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption == null ? "" : caption;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(String addedAt) {
        this.addedAt = addedAt == null ? "" : addedAt;
    }

    /** File size in bytes, or -1 when not looked up. */
    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    /** True when the image file is still present in the photos folder. */
    public boolean fileExists() {
        return app.kinderjournal.util.AppPaths.photoExists(file);
    }

    /** Absolute path of the image, or null when the name is blank. */
    public java.nio.file.Path path() {
        return app.kinderjournal.util.AppPaths.photoFile(file);
    }

    /** A readable size such as "1.4 MB", or "" when unknown. */
    public String sizeText() {
        if (sizeBytes < 0) {
            return "";
        }
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }

    /** Caption, or the file name when no caption was written. */
    public String displayLabel() {
        return Text.isBlank(caption) ? file : caption;
    }
}
