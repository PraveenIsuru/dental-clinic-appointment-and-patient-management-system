package lk.dentalclinic.model;

import java.time.LocalDate;
import java.time.Period;

/**
 * A patient of the clinic.
 *
 * <p>BUILDER pattern. The alternative was a seven-argument constructor in which
 * {@code address} and {@code contactNumber} are adjacent strings — easy to transpose
 * and impossible for the compiler to catch.
 */
public final class Patient extends Person {

    private final String patientNo;
    private final String address;
    private final LocalDate dateOfBirth;

    private Patient(Builder builder) {
        super(builder.id, builder.fullName, builder.contactNumber, builder.email, builder.userId);
        this.patientNo = builder.patientNo;
        this.address = builder.address;
        this.dateOfBirth = builder.dateOfBirth;
    }

    public String getPatientNo() {
        return patientNo;
    }

    public String getAddress() {
        return address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /** Age in whole years, or {@code -1} when the date of birth is not recorded. */
    public int age() {
        return dateOfBirth == null ? -1 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    @Override
    protected String displayLabel() {
        return patientNo == null ? fullName : fullName + " (" + patientNo + ")";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int id;
        private String patientNo;
        private Integer userId;
        private String fullName;
        private String address;
        private String contactNumber;
        private String email;
        private LocalDate dateOfBirth;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder patientNo(String patientNo) {
            this.patientNo = patientNo;
            return this;
        }

        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder contactNumber(String contactNumber) {
            this.contactNumber = contactNumber;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder dateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public Patient build() {
            return new Patient(this);
        }
    }
}
