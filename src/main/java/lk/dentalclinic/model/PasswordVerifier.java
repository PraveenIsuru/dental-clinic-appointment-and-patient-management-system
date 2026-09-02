package lk.dentalclinic.model;

/**
 * Verifies a candidate password against a stored hash.
 *
 * <p>Declared here, in {@code model}, rather than in {@code security} so that
 * {@link User} can check its own password without the domain model depending on
 * the hashing implementation. {@code PasswordHasher::verify} satisfies it.
 */
@FunctionalInterface
public interface PasswordVerifier {

    boolean verify(char[] candidate, String storedHash);
}
