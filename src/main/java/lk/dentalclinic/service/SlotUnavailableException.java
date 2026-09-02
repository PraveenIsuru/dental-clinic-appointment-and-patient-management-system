package lk.icbt.dentalclinic.service;

import java.time.LocalTime;
import java.util.List;

/**
 * The requested time is already taken for that dentist.
 *
 * <p>Carries the nearest free slots so the refusal can be useful rather than merely
 * correct: "that time is taken, the next free are 10:30, 11:00 and 11:30" saves the
 * receptionist a second lookup while the patient is still on the telephone.
 *
 * <p>Raised from two places — the optimistic availability check, and the translation of
 * a {@code uq_dentist_slot} violation when two bookings race. Both produce the same
 * message, so the user cannot tell which one fired.
 */
public class SlotUnavailableException extends RuntimeException {

    private final transient List<LocalTime> suggestions;

    public SlotUnavailableException(LocalTime requested, List<LocalTime> suggestions) {
        super("The " + requested + " slot is already booked for that dentist.");
        this.suggestions = List.copyOf(suggestions);
    }

    public List<LocalTime> suggestions() {
        return suggestions;
    }

    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }
}
