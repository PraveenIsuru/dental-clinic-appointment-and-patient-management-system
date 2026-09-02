package lk.dentalclinic.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    @DisplayName("hash produces the documented self-describing format")
    void hashFormatIsSelfDescribing() {
        String stored = hasher.hash("Admin@123");
        String[] parts = stored.split("\\$");

        assertEquals(4, parts.length, stored);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals(String.valueOf(PasswordHasher.ITERATIONS), parts[1]);
        assertFalse(parts[2].isBlank(), "salt must be present");
        assertFalse(parts[3].isBlank(), "hash must be present");
    }

    @Test
    @DisplayName("the correct password verifies")
    void correctPasswordVerifies() {
        String stored = hasher.hash("Admin@123");

        assertTrue(hasher.verify("Admin@123", stored));
    }

    @Test
    @DisplayName("a wrong password does not verify")
    void wrongPasswordFails() {
        String stored = hasher.hash("Admin@123");

        assertFalse(hasher.verify("admin@123", stored), "must be case sensitive");
        assertFalse(hasher.verify("Admin@1234", stored));
        assertFalse(hasher.verify("", stored));
    }

    @Test
    @DisplayName("the same password hashed twice yields different output - the salt is random")
    void saltMakesEveryHashUnique() {
        String first = hasher.hash("Patient@123");
        String second = hasher.hash("Patient@123");

        assertNotEquals(first, second,
                "identical hashes would mean an unsalted digest, as in the reference project");
        // Both must still verify against the one password.
        assertTrue(hasher.verify("Patient@123", first));
        assertTrue(hasher.verify("Patient@123", second));
    }

    @ParameterizedTest
    @DisplayName("a malformed stored value is rejected rather than throwing")
    @ValueSource(strings = {
            "",
            "not-a-hash",
            "pbkdf2-sha256$210000$onlythreeparts",
            "bcrypt$10$abc$def",
            "pbkdf2-sha256$notanumber$c2FsdA$aGFzaA",
            "pbkdf2-sha256$210000$!!!not-base64!!!$aGFzaA"
    })
    void malformedStoredValueIsRejected(String stored) {
        // Must return false, never throw: an exception here could be timed or observed
        // and would distinguish a corrupt row from a merely wrong password.
        assertFalse(hasher.verify("anything", stored));
    }

    @Test
    @DisplayName("a null stored value is rejected")
    void nullStoredValueIsRejected() {
        assertFalse(hasher.verify("anything", null));
    }

    @Test
    @DisplayName("a hash at the current cost does not need rehashing; a weaker one does")
    void needsRehashDetectsWeakerCost() {
        assertFalse(hasher.needsRehash(hasher.hash("Admin@123")));

        assertTrue(hasher.needsRehash("pbkdf2-sha256$1000$c2FsdA$aGFzaA"),
                "1,000 iterations is far below the current cost");
        assertTrue(hasher.needsRehash("plaintext"));
        assertTrue(hasher.needsRehash(null));
    }

    @Test
    @DisplayName("the seeded admin hash in V3__seed.sql verifies against its documented password")
    void seededHashesAreValid() {
        // Guards against the seed file drifting from the documented demo credentials.
        String seededAdmin =
                "pbkdf2-sha256$210000$U6s/Xe4C0h95yYJWgWySog$dxcnEnvMBAPYVEpweedKn7z+6970frtYTxWwWbRnuFI";

        assertTrue(hasher.verify("Admin@123", seededAdmin),
                "database/V3__seed.sql documents admin / Admin@123");
        assertFalse(hasher.verify("Admin@1234", seededAdmin));
    }

    @Test
    @DisplayName("wipe clears the password array")
    void wipeClearsTheArray() {
        char[] password = "Admin@123".toCharArray();

        PasswordHasher.wipe(password);

        assertEquals("\0\0\0\0\0\0\0\0\0", new String(password));
    }
}
