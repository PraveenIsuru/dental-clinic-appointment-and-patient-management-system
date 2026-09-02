package lk.dentalclinic.model;

/** Written by the database triggers in V2__routines.sql, never by application code. */
public enum AuditAction {
    INSERT,
    UPDATE,
    DELETE;

    public static AuditAction of(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
