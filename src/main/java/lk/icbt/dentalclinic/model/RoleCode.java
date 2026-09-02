package lk.icbt.dentalclinic.model;

/** The three roles of assumption A3. RECEPTIONIST is folded into ADMIN (A4). */
public enum RoleCode {
    ADMIN,
    DENTIST,
    PATIENT;

    public static RoleCode of(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
