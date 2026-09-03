package com.mycompany.sunrisedentalclinic;

import com.mycompany.sunrisedentalclinic.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void samePasswordShouldProduceSameHash() {

        String first =
                PasswordUtil.hashPassword("admin123");

        String second =
                PasswordUtil.hashPassword("admin123");

        assertEquals(first, second);
    }

    @Test
    void differentPasswordsShouldProduceDifferentHashes() {

        String first =
                PasswordUtil.hashPassword("admin123");

        String second =
                PasswordUtil.hashPassword("wrongPassword");

        assertNotEquals(first, second);
    }

    @Test
    void generatedHashShouldNotContainPlainPassword() {

        String password = "admin123";

        String hash =
                PasswordUtil.hashPassword(password);

        assertNotEquals(password, hash);
    }

    @Test
    void generatedHashShouldNotBeNull() {

        assertNotNull(
                PasswordUtil.hashPassword("admin123")
        );
    }

    @Test
    void generatedSha256HashShouldHave64Characters() {

        String hash =
                PasswordUtil.hashPassword("admin123");

        assertEquals(
                64,
                hash.length()
        );
    }

    @Test
    void passwordHashShouldMatchExpectedAdminHash() {

        String expected =
                "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

        assertEquals(
                expected,
                PasswordUtil.hashPassword("admin123")
        );
    }
}