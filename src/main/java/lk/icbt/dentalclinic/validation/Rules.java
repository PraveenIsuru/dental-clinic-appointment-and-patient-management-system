package lk.icbt.dentalclinic.validation;

import java.util.regex.Pattern;

/**
 * Reusable field checks, shared by every form.
 *
 * <p>Centralised so that "what counts as a valid contact number" has one answer. When
 * the same rule is re-written inline at each call site, the registration form and the
 * booking form drift apart and a number accepted by one is rejected by the other.
 */
public final class Rules {

    /**
     * Sri Lankan mobile and landline numbers: ten digits beginning 0, or the same in
     * international form (+94 followed by nine digits). Spaces and hyphens are stripped
     * before matching, so a user may type 077 123 4567.
     */
    private static final Pattern PHONE = Pattern.compile("^(?:0\\d{9}|\\+94\\d{9})$");

    /**
     * Pragmatic email check: something, an @, something with a dot, no spaces. RFC 5322
     * in full is famously unmatchable by a sane regex, and rejecting an address a real
     * mail server would accept is worse than accepting one it would bounce.
     */
    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    /** Letters, digits, dot, underscore and hyphen; 3 to 50 characters. */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private Rules() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isPresent(String value) {
        return !isBlank(value);
    }

    public static boolean lengthAtMost(String value, int max) {
        return value == null || value.trim().length() <= max;
    }

    public static boolean lengthAtLeast(String value, int min) {
        return value != null && value.trim().length() >= min;
    }

    public static boolean isPhone(String value) {
        return isPresent(value) && PHONE.matcher(normalisePhone(value)).matches();
    }

    /** Strips the separators people type, so validation and storage agree. */
    public static String normalisePhone(String value) {
        return value == null ? "" : value.replaceAll("[\\s()-]", "");
    }

    public static boolean isEmail(String value) {
        return isPresent(value) && EMAIL.matcher(value.trim()).matches();
    }

    public static boolean isUsername(String value) {
        return isPresent(value) && USERNAME.matcher(value.trim()).matches();
    }

    /**
     * Password policy: at least ten characters, with a letter and a digit.
     *
     * <p>Length is weighted over character-class rules deliberately. Mandating symbols
     * pushes users towards {@code Password1!} — short, predictable and in every
     * cracking dictionary — whereas length is what actually costs an attacker time.
     */
    public static boolean isAcceptablePassword(char[] password) {
        if (password == null || password.length < 10) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : password) {
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return letter && digit;
    }
}
