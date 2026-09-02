package lk.dentalclinic.model;

public enum BillStatus {
    ISSUED,
    PAID,
    VOID;

    public static BillStatus of(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
