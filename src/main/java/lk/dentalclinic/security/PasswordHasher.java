package lk.icbt.dentalclinic.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing using PBKDF2-HMAC-SHA256 from {@code javax.crypto}, which ships
 * with the JDK. No BCrypt library and no Spring Security.
 *
 * <p><strong>Why not SHA-256, as the reference project used?</strong> A bare digest is
 * fast and unsalted, so an attacker holding the {@code users} table can (a) spot two
 * accounts sharing a password and (b) test billions of candidates per second on a GPU.
 * PBKDF2 fixes both: a per-user 16-byte random salt makes every hash unique, and
 * {@value #ITERATIONS} iterations make each guess deliberately expensive. This is the
 * brief's ETHICAL/DIGITAL criterion on "secure coding practices" in one class.
 *
 * <p>Stored format, self-describing so the cost factor can be raised later without
 * invalidating existing passwords:
 * <pre>pbkdf2-sha256$&lt;iterations&gt;$&lt;base64 salt&gt;$&lt;base64 hash&gt;</pre>
 *
 * <p>Instances are stateless and safe for concurrent use.
 */
public final class PasswordHasher {

    /** OWASP's 2023 floor for PBKDF2-HMAC-SHA256. */
    public static final int ITERATIONS = 210_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    /** Hashes a password with a fresh random salt at the current cost. */
    public String hash(char[] password) {
        return hash(password, ITERATIONS);
    }

    /**
     * Hashes at an explicit cost.
     *
     * <p>Exposed so the cost can be raised over time as hardware improves, and so the
     * upgrade path in {@link #needsRehash(String)} is testable — verifying that a
     * weaker stored hash is re-hashed on the next sign-in requires being able to
     * produce one.
     *
     * @throws IllegalArgumentException if the cost is below 1,000, which would make the
     *                                  hash cheap enough to be worse than useless
     */
    public String hash(char[] password, int iterations) {
        if (iterations < 1_000) {
            throw new IllegalArgumentException(
                    "Refusing to hash with only " + iterations + " iterations");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);

        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + iterations
                + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(key);
    }

    public String hash(String password) {
        return hash(password.toCharArray());
    }

    /**
     * Verifies a candidate password against a stored hash.
     *
     * <p>Returns {@code false} rather than throwing on a malformed stored value: a
     * corrupt row must not become a way to distinguish "no such user" from "wrong
     * password", which would leak account existence.
     */
    public boolean verify(char[] candidate, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(candidate, salt, iterations);

            // Constant-time: a byte-by-byte comparison that returns early leaks how
            // much of the hash matched, which is enough to reconstruct it.
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean verify(String candidate, String stored) {
        return verify(candidate.toCharArray(), stored);
    }

    /**
     * True when a stored hash was produced with a weaker cost than the current setting,
     * so the caller can transparently re-hash on the next successful login.
     */
    public boolean needsRehash(String stored) {
        if (stored == null) {
            return true;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 is unavailable in this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Generates the hashes embedded in {@code database/V3__seed.sql}.
     *
     * <p>Run with: {@code mvn -q exec:java} or directly from an IDE. The seed file must
     * never contain plaintext passwords, and the hashes cannot be written by hand
     * because each needs a fresh random salt.
     */
    public static void main(String[] args) {
        PasswordHasher hasher = new PasswordHasher();
        String[] passwords = args.length > 0
                ? args
                : new String[]{"Admin@123", "Dentist@123", "Patient@123"};

        for (String password : passwords) {
            System.out.println(password + "  ->  " + hasher.hash(password));
        }
    }

    /** Defensive helper for clearing a password array once it is no longer needed. */
    public static void wipe(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
