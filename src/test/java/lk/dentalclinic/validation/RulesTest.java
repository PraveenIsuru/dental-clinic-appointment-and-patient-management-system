package lk.dentalclinic.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and invalid data for the shared field rules.
 *
 * <p>The invalid cases matter more than the valid ones: a rule that accepts good input
 * but also accepts bad input passes a naive test and fails in production.
 */
class RulesTest {

    @ParameterizedTest
    @DisplayName("valid Sri Lankan contact numbers are accepted, separators and all")
    @ValueSource(strings = {
            "0771234567",
            "077 123 4567",
            "077-123-4567",
            "0112345678",
            "+94771234567"
    })
    void acceptsValidPhoneNumbers(String number) {
        assertTrue(Rules.isPhone(number), number);
    }

    @ParameterizedTest
    @DisplayName("malformed contact numbers are rejected")
    @ValueSource(strings = {
            "077123456",       // nine digits
            "07712345678",     // eleven digits
            "1771234567",      // does not begin 0
            "+9477123456",     // too short after +94
            "abcdefghij",
            "077-123-456a",
            ""
    })
    void rejectsInvalidPhoneNumbers(String number) {
        assertFalse(Rules.isPhone(number), number);
    }

    @Test
    @DisplayName("normalising strips the separators people type")
    void normalisesPhone() {
        assertEquals("0771234567", Rules.normalisePhone("077 123-4567"));
        assertEquals("0771234567", Rules.normalisePhone("(077) 1234567"));
        assertEquals("", Rules.normalisePhone(null));
    }

    @ParameterizedTest
    @DisplayName("plausible email addresses are accepted")
    @ValueSource(strings = {"a@b.lk", "kasun.fernando@example.com", "x+tag@sub.domain.co.uk"})
    void acceptsValidEmail(String email) {
        assertTrue(Rules.isEmail(email), email);
    }

    @ParameterizedTest
    @DisplayName("malformed email addresses are rejected")
    @ValueSource(strings = {"no-at-sign", "@no-local.lk", "no-domain@", "two@@at.lk",
            "spaces in@example.lk", "trailing@dot."})
    void rejectsInvalidEmail(String email) {
        assertFalse(Rules.isEmail(email), email);
    }

    @ParameterizedTest
    @DisplayName("usernames accept the documented character set")
    @ValueSource(strings = {"abc", "dr.perera", "kasun_f", "user-01", "a1234567890"})
    void acceptsValidUsername(String username) {
        assertTrue(Rules.isUsername(username), username);
    }

    @ParameterizedTest
    @DisplayName("usernames reject spaces, symbols and out-of-range lengths")
    @ValueSource(strings = {"ab", "has space", "semi;colon", "quote'drop", "<script>"})
    void rejectsInvalidUsername(String username) {
        assertFalse(Rules.isUsername(username), username);
    }

    @Test
    @DisplayName("username length boundaries: 3 accepted, 2 rejected, 50 accepted, 51 rejected")
    void usernameLengthBoundaries() {
        assertTrue(Rules.isUsername("abc"));
        assertFalse(Rules.isUsername("ab"));
        assertTrue(Rules.isUsername("a".repeat(50)));
        assertFalse(Rules.isUsername("a".repeat(51)));
    }

    @Test
    @DisplayName("password policy: 10 characters with a letter and a digit")
    void passwordPolicy() {
        assertTrue(Rules.isAcceptablePassword("Patient123".toCharArray()));
        assertTrue(Rules.isAcceptablePassword("correct1horse".toCharArray()));

        assertFalse(Rules.isAcceptablePassword("Short1".toCharArray()), "too short");
        assertFalse(Rules.isAcceptablePassword("abcdefghijkl".toCharArray()), "no digit");
        assertFalse(Rules.isAcceptablePassword("123456789012".toCharArray()), "no letter");
        assertFalse(Rules.isAcceptablePassword(null));
        assertFalse(Rules.isAcceptablePassword(new char[0]));
    }

    @Test
    @DisplayName("password length boundary: 10 accepted, 9 rejected")
    void passwordLengthBoundary() {
        assertTrue(Rules.isAcceptablePassword("abcdefghi1".toCharArray()));
        assertFalse(Rules.isAcceptablePassword("abcdefgh1".toCharArray()));
    }

    @Test
    @DisplayName("blank checks treat whitespace as absent")
    void blankTreatsWhitespaceAsAbsent() {
        assertTrue(Rules.isBlank(null));
        assertTrue(Rules.isBlank(""));
        assertTrue(Rules.isBlank("   "));
        assertTrue(Rules.isBlank("\t\n"));
        assertFalse(Rules.isBlank(" x "));
        assertTrue(Rules.isPresent(" x "));
    }

    @Test
    @DisplayName("length checks trim before measuring")
    void lengthChecksTrim() {
        assertTrue(Rules.lengthAtMost("  abc  ", 3));
        assertFalse(Rules.lengthAtMost("abcd", 3));
        assertTrue(Rules.lengthAtLeast("  abc  ", 3));
        assertFalse(Rules.lengthAtLeast("ab", 3));
    }
}
