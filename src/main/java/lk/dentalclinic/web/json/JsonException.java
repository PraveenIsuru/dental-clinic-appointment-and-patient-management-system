package lk.dentalclinic.web.json;

/**
 * The request body was not valid JSON, or a required field was missing.
 *
 * <p>Always a client error: handlers map it to <strong>400 Bad Request</strong>. The
 * message names the position or the field, because an API client debugging a malformed
 * request has nothing else to go on.
 */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }
}
