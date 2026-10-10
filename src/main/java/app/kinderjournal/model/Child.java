package app.kinderjournal.model;

import app.kinderjournal.util.Dates;
import app.kinderjournal.util.Text;

import java.time.LocalDate;

/** A child's profile: the anchor every other record points at. */
public class Child {

    private long id;
    private String firstName = "";
    private String lastName = "";
    private String birthDate;          // ISO yyyy-MM-dd, may be null
    private String gender = "";
    private long groupId;              // 0 = not assigned
    private String startDate;          // first day at kindergarten
    private String guardianName = "";
    private String guardianRelation = "";
    private String guardianPhone = "";
    private String guardianEmail = "";
    private String secondContact = "";
    private String address = "";
    private String allergies = "";
    private String notes = "";
    private String photoFile;          // file name inside the photos folder
    private boolean archived;
    private String createdAt = "";
    private String updatedAt = "";

    /** Filled in by list queries: the group name, or "" when unassigned. */
    private String groupName = "";

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName == null ? "" : firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName == null ? "" : lastName;
    }

    /** "Anna Berg" */
    public String fullName() {
        return Text.joinNonBlank(" ", firstName, lastName);
    }

    /** "Berg, Anna" - used for alphabetical lists and reports. */
    public String sortName() {
        if (lastName.isBlank()) {
            return firstName;
        }
        if (firstName.isBlank()) {
            return lastName;
        }
        return lastName + ", " + firstName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = Text.nullIfBlank(birthDate);
    }

    public LocalDate birthLocalDate() {
        return Dates.fromIso(birthDate);
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender == null ? "" : gender;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = Text.nullIfBlank(startDate);
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName == null ? "" : guardianName;
    }

    public String getGuardianRelation() {
        return guardianRelation;
    }

    public void setGuardianRelation(String guardianRelation) {
        this.guardianRelation = guardianRelation == null ? "" : guardianRelation;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone == null ? "" : guardianPhone;
    }

    public String getGuardianEmail() {
        return guardianEmail;
    }

    public void setGuardianEmail(String guardianEmail) {
        this.guardianEmail = guardianEmail == null ? "" : guardianEmail;
    }

    public String getSecondContact() {
        return secondContact;
    }

    public void setSecondContact(String secondContact) {
        this.secondContact = secondContact == null ? "" : secondContact;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies == null ? "" : allergies;
    }

    /** Free-text teacher notes held on the profile itself. */
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public String getPhotoFile() {
        return photoFile;
    }

    public void setPhotoFile(String photoFile) {
        this.photoFile = Text.nullIfBlank(photoFile);
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? "" : groupName;
    }

    /** Age in whole years today, or -1 when the birth date is unknown. */
    public int ageYears() {
        return Dates.ageInYears(birthLocalDate(), LocalDate.now());
    }

    /** "4 years 3 months", or "" when the birth date is unknown. */
    public String ageText() {
        return Dates.ageText(birthLocalDate(), LocalDate.now());
    }

    /** "Born 12 Mar 2020 (4 years)" style summary. */
    public String birthSummary() {
        if (birthLocalDate() == null) {
            return "";
        }
        String age = ageText();
        return "Born " + Dates.display(birthDate) + (age.isEmpty() ? "" : " (" + age + ")");
    }

    /** Two initials for the avatar placeholder. */
    public String initials() {
        return Text.initials(fullName().isEmpty() ? "?" : fullName());
    }

    @Override
    public String toString() {
        return fullName();
    }
}
