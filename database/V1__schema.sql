-- =====================================================================
--  Sunrise Dental Clinic - V1 schema
--  CIS6003 WRIT1
--
--  Run order:  V1__schema.sql  ->  V2__routines.sql  ->  V3__seed.sql
--
--      mysql -u root -p < database/V1__schema.sql
--
--  Table design adapted from a peer's JavaFX prototype (see README
--  acknowledgement), then extended with audit logging, a settings table,
--  an appointment sequence, bill line items and migration tracking.
-- =====================================================================

DROP DATABASE IF EXISTS sunrise_clinic;
CREATE DATABASE sunrise_clinic
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE sunrise_clinic;


-- ---------------------------------------------------------------------
-- Migration tracking. Hand-rolled equivalent of Flyway's schema_history:
-- every change to this database is a numbered, reviewable file, which is
-- part of the Task D version-control story.
-- ---------------------------------------------------------------------
CREATE TABLE schema_version (
    version      VARCHAR(20)  NOT NULL PRIMARY KEY,
    description  VARCHAR(200) NOT NULL,
    applied_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Clinic-wide settings. Consultation fee and tax rate live here rather
-- than as constants in Java, so fn_appointment_total can read them and
-- the clinic can change a price without a redeploy.
-- ---------------------------------------------------------------------
CREATE TABLE clinic_settings (
    setting_key    VARCHAR(60)   NOT NULL PRIMARY KEY,
    setting_value  VARCHAR(200)  NOT NULL,
    description    VARCHAR(255),
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Roles. A lookup table rather than an ENUM column: adding a role must
-- not require an ALTER TABLE, and the foreign key documents the
-- relationship in the class diagram.
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    role_id      INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    description  VARCHAR(120) NOT NULL
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Users. One login per person, whatever their role.
--
-- password_hash holds a self-describing PBKDF2 string
--   pbkdf2-sha256$210000$<b64 salt>$<b64 hash>
-- produced by lk.icbt.dentalclinic.security.PasswordHasher. Plaintext
-- and bare digests are never stored - see V3__seed.sql.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id                INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,
    full_name              VARCHAR(120) NOT NULL,
    email                  VARCHAR(160) UNIQUE,
    role_id                INT          NOT NULL,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           DATETIME     NULL,
    last_login_at          DATETIME     NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (role_id),
    INDEX idx_users_role (role_id)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Patients. user_id is nullable on purpose: a patient registered at the
-- desk by an administrator has no login, while a self-registered patient
-- does. See assumption A2 in my-docs/task-a/design-decisions.md.
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    patient_id      INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_no      VARCHAR(20)  NOT NULL UNIQUE,
    user_id         INT          NULL UNIQUE,
    full_name       VARCHAR(120) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    contact_number  VARCHAR(20)  NOT NULL,
    email           VARCHAR(160),
    date_of_birth   DATE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    INDEX idx_patients_name (full_name),
    INDEX idx_patients_contact (contact_number)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Dentists. session_start / session_end bound the hours a dentist works,
-- which the booking validator checks in addition to clinic hours.
-- ---------------------------------------------------------------------
CREATE TABLE dentists (
    dentist_id      INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         INT          NULL UNIQUE,
    full_name       VARCHAR(120) NOT NULL,
    specialization  VARCHAR(120) NOT NULL DEFAULT 'General Dentistry',
    phone           VARCHAR(20),
    email           VARCHAR(160),
    session_start   TIME         NOT NULL DEFAULT '08:00:00',
    session_end     TIME         NOT NULL DEFAULT '20:00:00',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_dentists_user FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT chk_dentist_session CHECK (session_end > session_start)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Treatments. `family` groups treatments so PricingStrategyFactory can
-- resolve one strategy per family rather than one per row.
-- ---------------------------------------------------------------------
CREATE TABLE treatments (
    treatment_id      INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(20)    NOT NULL UNIQUE,
    name              VARCHAR(120)   NOT NULL UNIQUE,
    family            VARCHAR(30)    NOT NULL,
    description       VARCHAR(255),
    base_cost         DECIMAL(10, 2) NOT NULL,
    duration_minutes  INT            NOT NULL DEFAULT 30,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_treatment_cost CHECK (base_cost >= 0),
    CONSTRAINT chk_treatment_duration CHECK (duration_minutes > 0)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Appointments - the core table.
--
-- uq_dentist_slot is the direct answer to the double-booking problem
-- named in the scenario. The service layer checks availability first and
-- returns a friendly message, but this constraint is what makes the rule
-- true even under two simultaneous requests, or a direct API call that
-- bypasses the UI entirely.
-- ---------------------------------------------------------------------
CREATE TABLE appointments (
    appointment_id    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_no    VARCHAR(20)  NOT NULL UNIQUE,
    patient_id        INT          NOT NULL,
    dentist_id        INT          NOT NULL,
    treatment_id      INT          NOT NULL,
    appointment_date  DATE         NOT NULL,
    appointment_time  TIME         NOT NULL,
    status            ENUM('BOOKED', 'CONFIRMED', 'COMPLETED', 'CANCELLED')
                                   NOT NULL DEFAULT 'BOOKED',
    notes             VARCHAR(500),
    created_by        INT          NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_appt_patient   FOREIGN KEY (patient_id)   REFERENCES patients (patient_id),
    CONSTRAINT fk_appt_dentist   FOREIGN KEY (dentist_id)   REFERENCES dentists (dentist_id),
    CONSTRAINT fk_appt_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (treatment_id),
    CONSTRAINT fk_appt_creator   FOREIGN KEY (created_by)   REFERENCES users (user_id)
        ON DELETE SET NULL,

    -- The business rule the clinic actually asked for.
    CONSTRAINT uq_dentist_slot UNIQUE (dentist_id, appointment_date, appointment_time),

    INDEX idx_appt_date (appointment_date),
    INDEX idx_appt_patient_date (patient_id, appointment_date),
    INDEX idx_appt_status (status)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Appointment number sequence, one row per year, so that APT-2026-0001
-- restarts each January. A dedicated row makes allocation atomic under
-- concurrency - see sp_next_appointment_no in V2.
-- ---------------------------------------------------------------------
CREATE TABLE appointment_sequence (
    seq_year     INT NOT NULL PRIMARY KEY,
    last_number  INT NOT NULL DEFAULT 0
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Bills. One bill per appointment (enforced by the UNIQUE on
-- appointment_id). total_amount is computed by a trigger, never trusted
-- from the client - see trg_bill_before_insert in V2.
-- ---------------------------------------------------------------------
CREATE TABLE bills (
    bill_id           INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bill_no           VARCHAR(20)    NOT NULL UNIQUE,
    appointment_id    INT            NOT NULL UNIQUE,
    consultation_fee  DECIMAL(10, 2) NOT NULL DEFAULT 0,
    treatment_charge  DECIMAL(10, 2) NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(10, 2) NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status            ENUM('ISSUED', 'PAID', 'VOID') NOT NULL DEFAULT 'ISSUED',
    issued_by         INT            NULL,
    issued_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at           DATETIME       NULL,

    CONSTRAINT fk_bill_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (appointment_id),
    CONSTRAINT fk_bill_issuer FOREIGN KEY (issued_by) REFERENCES users (user_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_bill_amounts CHECK (
        consultation_fee >= 0 AND treatment_charge >= 0
        AND discount_amount >= 0 AND tax_amount >= 0
    )
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Bill line items. COMPOSITION: a line cannot exist without its bill,
-- so the cascade delete here matches the filled diamond in the class
-- diagram.
-- ---------------------------------------------------------------------
CREATE TABLE bill_line_items (
    line_id      INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bill_id      INT            NOT NULL,
    description  VARCHAR(200)   NOT NULL,
    quantity     INT            NOT NULL DEFAULT 1,
    unit_price   DECIMAL(10, 2) NOT NULL,
    line_total   DECIMAL(10, 2) NOT NULL,

    CONSTRAINT fk_line_bill FOREIGN KEY (bill_id) REFERENCES bills (bill_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_line_quantity CHECK (quantity > 0),
    INDEX idx_line_bill (bill_id)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Audit log, written by triggers rather than by application code, so an
-- update issued straight from a SQL client is recorded too. Supports the
-- brief's ETHICAL criterion on protecting and accounting for user data.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    audit_id     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_name   VARCHAR(60)  NOT NULL,
    record_id    INT          NOT NULL,
    action       ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    changed_by   INT          NULL,
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_values   JSON         NULL,
    new_values   JSON         NULL,

    INDEX idx_audit_record (table_name, record_id),
    INDEX idx_audit_time (changed_at)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- Help topics - brief requirement 5, "step-by-step instructions for new
-- staff". Held in the database so an administrator can edit them without
-- a redeploy.
-- ---------------------------------------------------------------------
CREATE TABLE help_topics (
    topic_id       INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(160) NOT NULL,
    body           TEXT         NOT NULL,
    audience       VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    display_order  INT          NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_help_order (display_order)
) ENGINE = InnoDB;


INSERT INTO schema_version (version, description)
VALUES ('V1', 'Base schema: 13 tables, foreign keys, uq_dentist_slot');

SELECT 'V1 schema created' AS status,
       COUNT(*) AS tables_created
FROM information_schema.tables
WHERE table_schema = 'sunrise_clinic';
