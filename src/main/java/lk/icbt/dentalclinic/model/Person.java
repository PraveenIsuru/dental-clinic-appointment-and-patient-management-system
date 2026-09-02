package lk.icbt.dentalclinic.model;

import java.util.Objects;

/**
 * Shared identity of the people the clinic deals with.
 *
 * <p>{@link Patient} and {@link Dentist} specialise this. {@link User} deliberately
 * does not: credentials are <em>associated</em> with a person rather than inherited
 * by one, because a patient registered at the desk has a record but no login
 * (assumption A5). That is why the class diagram draws {@code Person 0..1 -- 0..1 User}
 * as an association and not as a generalisation.
 *
 * <p>State is {@code protected} so subclasses can read it, and every field is final:
 * an entity loaded from the database is a snapshot, not a mutable handle on a row.
 */
public abstract class Person {

    protected final int id;
    protected final String fullName;
    protected final String contactNumber;
    protected final String email;
    protected final Integer userId;

    protected Person(int id, String fullName, String contactNumber, String email, Integer userId) {
        this.id = id;
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.contactNumber = contactNumber;
        this.email = email;
        this.userId = userId;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getEmail() {
        return email;
    }

    /** The linked login account, or empty when this person has no credentials (A5). */
    public Integer getUserId() {
        return userId;
    }

    public boolean hasLogin() {
        return userId != null;
    }

    /** How this person is identified in lists and audit messages. */
    protected abstract String displayLabel();

    @Override
    public String toString() {
        return displayLabel();
    }
}
