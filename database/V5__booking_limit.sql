-- =====================================================================
--  Sunrise Dental Clinic - V5
--  The self-service booking limit becomes a clinic setting
--
--      mysql -u root -p < database/V5__booking_limit.sql
--
--  Added during the REFACTOR step of the TDD cycle recorded in
--  my-docs/task-c/tdd-evidence.md. The rule was first implemented with
--  a constant in AppointmentService, which made the test pass; moving
--  it here lets the clinic change the limit without a redeploy, exactly
--  as the consultation fee and the opening hours already work.
--
--  The test did not change between the GREEN and REFACTOR steps. That
--  is what made the refactor safe, and it is the point of the cycle.
-- =====================================================================

USE sunrise_clinic;

INSERT INTO clinic_settings (setting_key, setting_value, description)
VALUES ('booking.max.upcoming', '3',
        'Upcoming appointments one self-service patient may hold at once')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO schema_version (version, description)
VALUES ('V5', 'booking.max.upcoming clinic setting')
ON DUPLICATE KEY UPDATE description = VALUES(description);

SELECT setting_key, setting_value, description
FROM clinic_settings WHERE setting_key = 'booking.max.upcoming';
