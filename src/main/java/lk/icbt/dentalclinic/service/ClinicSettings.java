package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dao.SettingsDao;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The clinic's operating rules, read from {@code clinic_settings}.
 *
 * <p>Loaded once and passed around as a value rather than each caller querying the
 * table: validating one booking touches four settings, and four round trips per
 * validation would be a silly cost for values that change perhaps twice a year.
 *
 * <p>Not cached permanently either — {@link #load(SettingsDao)} is called per request
 * by the service. An administrator changing the closing time should not have to wait
 * for a restart, and one indexed lookup per request is cheap. If that ever shows up in
 * a profile, a time-limited cache goes here and nowhere else.
 */
public record ClinicSettings(BigDecimal consultationFee,
                             BigDecimal taxRate,
                             BigDecimal maxDiscountPercent,
                             LocalTime opensAt,
                             LocalTime closesAt,
                             int slotMinutes,
                             String clinicName,
                             String clinicAddress) {

    public static ClinicSettings load(SettingsDao settings) {
        return new ClinicSettings(
                settings.requireDecimal("consultation.fee"),
                settings.requireDecimal("tax.rate"),
                settings.requireDecimal("discount.max.pct"),
                settings.requireTime("clinic.open"),
                settings.requireTime("clinic.close"),
                settings.requireInt("slot.minutes"),
                settings.require("clinic.name"),
                settings.require("clinic.address"));
    }

    /** Whether a time falls inside opening hours, end exclusive. */
    public boolean isWithinOpeningHours(LocalTime time) {
        return time != null && !time.isBefore(opensAt) && time.isBefore(closesAt);
    }

    /**
     * Whether a time sits on a slot boundary.
     *
     * <p>Without this, 09:00 and 09:05 are different slots to the database but the same
     * chair in reality — the unique index would happily accept both. Fixed slots are
     * what make {@code (dentist, date, time)} a meaningful uniqueness key.
     */
    public boolean isOnSlotBoundary(LocalTime time) {
        if (time == null) {
            return false;
        }
        if (time.getSecond() != 0 || time.getNano() != 0) {
            return false;
        }
        int minutesFromOpening = (time.getHour() * 60 + time.getMinute())
                - (opensAt.getHour() * 60 + opensAt.getMinute());
        return minutesFromOpening >= 0 && minutesFromOpening % slotMinutes == 0;
    }

    /** Every bookable start time in a day, in order. */
    public List<LocalTime> allSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime slot = opensAt; slot.isBefore(closesAt);
             slot = slot.plusMinutes(slotMinutes)) {
            slots.add(slot);
        }
        return slots;
    }
}
