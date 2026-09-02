package lk.dentalclinic.service;

import lk.dentalclinic.dao.DuplicateKeyException;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.dao.RoleDao;
import lk.dentalclinic.dao.UserDao;
import lk.dentalclinic.dao.jdbc.TransactionManager;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.Role;
import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.model.User;
import lk.dentalclinic.security.PasswordHasher;
import lk.dentalclinic.validation.Rules;
import lk.dentalclinic.validation.ValidationResult;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Patient self-registration — the portal of assumption A3.
 *
 * <p>FACADE: one call hides three DAOs, a validator, a password hash, a patient-number
 * allocation and a transaction. The handler above it needs to know none of that.
 *
 * <p>The user row and the patient row are created in <strong>one transaction</strong>.
 * Without it, a failure between the two would leave an account that can sign in but has
 * no patient record — a state no screen in the application knows how to display.
 */
public final class RegistrationService {

    private static final Logger LOG = Logger.getLogger(RegistrationService.class.getName());

    private final UserDao userDao;
    private final PatientDao patientDao;
    private final RoleDao roleDao;
    private final PasswordHasher hasher;
    private final TransactionManager transactions;

    public RegistrationService(UserDao userDao, PatientDao patientDao, RoleDao roleDao,
                               PasswordHasher hasher, TransactionManager transactions) {
        this.userDao = userDao;
        this.patientDao = patientDao;
        this.roleDao = roleDao;
        this.hasher = hasher;
        this.transactions = transactions;
    }

    /** Success carries the new patient; failure carries the field errors to redisplay. */
    public record Outcome(ValidationResult validation, Patient patient) {

        public boolean isSuccess() {
            return validation.isValid();
        }

        static Outcome rejected(ValidationResult validation) {
            return new Outcome(validation, null);
        }

        static Outcome created(Patient patient) {
            return new Outcome(ValidationResult.empty(), patient);
        }
    }

    public Outcome register(RegistrationRequest request) {
        ValidationResult validation = validate(request);
        if (validation.hasErrors()) {
            return Outcome.rejected(validation);
        }

        String passwordHash = hasher.hash(request.password());
        // The plaintext has served its purpose; do not leave it in memory.
        PasswordHasher.wipe(request.password());
        PasswordHasher.wipe(request.confirmPassword());

        try {
            Patient created = transactions.inTransaction(() -> {
                Role patientRole = roleDao.findByCode(RoleCode.PATIENT).orElseThrow(
                        () -> new IllegalStateException(
                                "The PATIENT role is missing; has database/V3__seed.sql been run?"));

                User user = User.builder()
                        .username(request.username().trim())
                        .fullName(request.fullName().trim())
                        .email(blankToNull(request.email()))
                        .role(patientRole)
                        .active(true)
                        .build();
                int userId = userDao.create(user, passwordHash);

                // Allocated inside the transaction: read-then-increment is only safe
                // while the surrounding transaction holds its locks.
                Patient patient = Patient.builder()
                        .patientNo(patientDao.nextPatientNo())
                        .userId(userId)
                        .fullName(request.fullName().trim())
                        .address(request.address().trim())
                        .contactNumber(Rules.normalisePhone(request.contactNumber()))
                        .email(blankToNull(request.email()))
                        .build();
                int patientId = patientDao.create(patient);

                return Patient.builder()
                        .id(patientId)
                        .patientNo(patient.getPatientNo())
                        .userId(userId)
                        .fullName(patient.getFullName())
                        .address(patient.getAddress())
                        .contactNumber(patient.getContactNumber())
                        .email(patient.getEmail())
                        .build();
            });

            LOG.info(() -> "Registered patient " + created.getPatientNo());
            return Outcome.created(created);

        } catch (DuplicateKeyException e) {
            // The uniqueness checks below are a courtesy; this is the guarantee. Two
            // simultaneous registrations of the same username both pass the check and
            // the database refuses the second.
            ValidationResult clash = ValidationResult.empty();
            if (e.getMessage() != null && e.getMessage().contains("email")) {
                clash.reject("email", "That email address is already registered.");
            } else {
                clash.reject("username", "That username is already taken.");
            }
            return Outcome.rejected(clash);
        }
    }

    ValidationResult validate(RegistrationRequest request) {
        ValidationResult result = ValidationResult.empty();

        result.rejectIf(Rules.isBlank(request.fullName()),
                "fullName", "Enter your full name.");
        result.rejectIf(!Rules.lengthAtMost(request.fullName(), 120),
                "fullName", "Your name must be 120 characters or fewer.");

        if (Rules.isBlank(request.username())) {
            result.reject("username", "Choose a username.");
        } else if (!Rules.isUsername(request.username())) {
            result.reject("username",
                    "Use 3 to 50 letters, digits, dots, underscores or hyphens.");
        } else if (userDao.existsByUsername(request.username().trim())) {
            result.reject("username", "That username is already taken.");
        }

        if (!Rules.isAcceptablePassword(request.password())) {
            result.reject("password",
                    "Use at least 10 characters, including a letter and a digit.");
        } else if (!java.util.Arrays.equals(request.password(), request.confirmPassword())) {
            result.reject("confirmPassword", "The two passwords do not match.");
        }

        result.rejectIf(Rules.isBlank(request.address()),
                "address", "Enter your address.");

        if (!Rules.isPhone(request.contactNumber())) {
            result.reject("contactNumber",
                    "Enter a contact number such as 0771234567 or +94771234567.");
        }

        if (Rules.isPresent(request.email())) {
            if (!Rules.isEmail(request.email())) {
                result.reject("email", "Enter a valid email address.");
            } else if (userDao.existsByEmail(request.email().trim())) {
                result.reject("email", "That email address is already registered.");
            }
        }

        return result;
    }

    private static String blankToNull(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }
}
