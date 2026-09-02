-- =====================================================================
--  Sunrise Dental Clinic - V4
--  Refine uq_dentist_slot so a cancelled appointment frees its slot
--
--      mysql -u root -p < database/V4__cancelled_slots.sql
--
--  THE PROBLEM, found while implementing M3
--  ----------------------------------------
--  V1 declared
--      UNIQUE (dentist_id, appointment_date, appointment_time)
--  which correctly prevents two live appointments in one slot. But the
--  index counts EVERY row, including cancelled ones. So once a patient
--  cancelled 10:00 with Dr Perera on a given day, that slot could never
--  be booked again by anyone -- the insert collided with a dead row.
--  A booking system that permanently destroys a slot on every
--  cancellation is worse than one with no constraint at all.
--
--  WHY NOT THE OBVIOUS FIXES
--  -------------------------
--  * Delete cancelled rows. Loses the audit trail and the cancellation
--    history the reports need, and the audit triggers already record
--    that the row existed.
--  * Enforce it only in Java. That is what the constraint exists to
--    back up: two concurrent bookings can both pass a service-layer
--    check, and a direct API call bypasses the check entirely.
--  * A partial / filtered unique index. SQL Server and PostgreSQL have
--    them; MySQL 8 does not.
--
--  THE FIX
--  -------
--  A generated column that is 1 for a live appointment and NULL for a
--  cancelled one, added to the unique key. MySQL treats NULLs in a
--  unique index as distinct from one another, so any number of
--  cancelled rows may share a slot while at most one live row can hold
--  it. Cancelling therefore frees the slot automatically, with no
--  application code involved: setting status = 'CANCELLED' recomputes
--  slot_active to NULL and the row leaves the constraint.
-- =====================================================================

USE sunrise_clinic;

-- VIRTUAL, not STORED: the value is derived from a column in the same
-- row, so it costs nothing to compute on read and no table rebuild to
-- add. MySQL 8 indexes virtual generated columns.
ALTER TABLE appointments
    ADD COLUMN slot_active TINYINT
        GENERATED ALWAYS AS (IF(status = 'CANCELLED', NULL, 1)) VIRTUAL;

-- The foreign key fk_appt_dentist has no index of its own: InnoDB was
-- satisfying it with the leftmost column of uq_dentist_slot, so dropping
-- that index outright fails with
--     ERROR 1553 Cannot drop index 'uq_dentist_slot': needed in a
--     foreign key constraint
-- Give the foreign key its own index first, then the unique key is free
-- to go. This one also serves "all appointments for a dentist", which
-- the schedule view runs on every page load.
CREATE INDEX idx_appt_dentist ON appointments (dentist_id);

ALTER TABLE appointments
    DROP INDEX uq_dentist_slot;

ALTER TABLE appointments
    ADD CONSTRAINT uq_dentist_slot
        UNIQUE (dentist_id, appointment_date, appointment_time, slot_active);

INSERT INTO schema_version (version, description)
VALUES ('V4', 'uq_dentist_slot ignores cancelled rows via a generated column');

SELECT 'V4 applied' AS status;
SHOW INDEX FROM appointments WHERE Key_name = 'uq_dentist_slot';
