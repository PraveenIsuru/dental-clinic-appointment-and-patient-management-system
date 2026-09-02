package lk.icbt.dentalclinic.service;

/**
 * A patient self-registration submission, as typed.
 *
 * <p>Carried as a DTO rather than as loose parameters so the validator, the service and
 * the form-redisplay path all agree on the shape, and so adding a field is one change
 * rather than four signatures.
 *
 * <p>The password is {@code char[]}, not {@code String}: a String lives in the constant
 * pool until garbage collection and would appear in a heap dump long after use. A char
 * array can be wiped as soon as it has been hashed, which
 * {@link RegistrationService} does.
 */
public record RegistrationRequest(String fullName,
                                  String username,
                                  char[] password,
                                  char[] confirmPassword,
                                  String email,
                                  String contactNumber,
                                  String address) {

    /** Never include the password in a log line or an error message. */
    @Override
    public String toString() {
        return "RegistrationRequest[" + username + "]";
    }
}
