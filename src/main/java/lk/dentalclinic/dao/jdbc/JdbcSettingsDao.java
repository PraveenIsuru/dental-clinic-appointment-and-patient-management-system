package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.DataAccessException;
import lk.icbt.dentalclinic.dao.SettingsDao;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class JdbcSettingsDao extends AbstractJdbcDao implements SettingsDao {

    public JdbcSettingsDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<String> find(String key) {
        return queryOne("SELECT setting_value FROM clinic_settings WHERE setting_key = ?",
                rs -> rs.getString("setting_value"), key);
    }

    @Override
    public Map<String, String> findAll() {
        Map<String, String> settings = new LinkedHashMap<>();
        query("SELECT setting_key, setting_value FROM clinic_settings ORDER BY setting_key",
                rs -> Map.entry(rs.getString("setting_key"), rs.getString("setting_value")))
                .forEach(entry -> settings.put(entry.getKey(), entry.getValue()));
        return settings;
    }

    @Override
    public String require(String key) {
        return find(key).orElseThrow(() -> new DataAccessException(
                "Missing clinic setting '" + key + "'. Has database/V3__seed.sql been run?"));
    }

    @Override
    public BigDecimal requireDecimal(String key) {
        String raw = require(key);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new DataAccessException(
                    "Clinic setting '" + key + "' must be a number, was: " + raw, e);
        }
    }

    @Override
    public int requireInt(String key) {
        return requireDecimal(key).intValueExact();
    }

    @Override
    public LocalTime requireTime(String key) {
        String raw = require(key).trim();
        try {
            // Accepts both "08:00" and "08:00:00".
            return LocalTime.parse(raw.length() == 5 ? raw + ":00" : raw);
        } catch (DateTimeParseException e) {
            throw new DataAccessException(
                    "Clinic setting '" + key + "' must be a time such as 08:00, was: " + raw, e);
        }
    }
}
