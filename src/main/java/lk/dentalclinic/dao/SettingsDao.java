package lk.dentalclinic.dao;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code clinic_settings} — consultation fee, tax rate, clinic hours, slot
 * length and the discount cap.
 *
 * <p>These live in the database rather than as Java constants so the clinic can
 * change a price or its opening hours without a redeploy (A8, A9).
 */
public interface SettingsDao {

    Optional<String> find(String key);

    Map<String, String> findAll();

    String require(String key);

    BigDecimal requireDecimal(String key);

    int requireInt(String key);

    LocalTime requireTime(String key);
}
