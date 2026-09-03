CREATE DATABASE IF NOT EXISTS sunrise_dental_clinic_2;
USE sunrise_dental_clinic_2;

CREATE TABLE IF NOT EXISTS users (
 user_id INT AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50) NOT NULL UNIQUE,
 password VARCHAR(255) NOT NULL,
 full_name VARCHAR(100) NOT NULL,
 email VARCHAR(120),
 role ENUM('ADMIN','RECEPTIONIST','DENTIST') NOT NULL,
 status BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dentists (
 dentist_id INT AUTO_INCREMENT PRIMARY KEY,
 full_name VARCHAR(100) NOT NULL,
 specialization VARCHAR(100),
 phone VARCHAR(30),
 email VARCHAR(120),
 start_time TIME NOT NULL DEFAULT '09:00:00',
 end_time TIME NOT NULL DEFAULT '17:00:00'
);

CREATE TABLE IF NOT EXISTS patients (
 patient_id INT AUTO_INCREMENT PRIMARY KEY,
 full_name VARCHAR(120) NOT NULL,
 gender VARCHAR(20),
 address VARCHAR(255),
 contact VARCHAR(30) NOT NULL,
 email VARCHAR(120),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS treatments (
 treatment_id INT AUTO_INCREMENT PRIMARY KEY,
 treatment_name VARCHAR(120) NOT NULL UNIQUE,
 description VARCHAR(255),
 cost DECIMAL(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS appointments (
 appointment_id INT AUTO_INCREMENT PRIMARY KEY,
 appointment_no VARCHAR(30) NOT NULL UNIQUE,
 patient_id INT NOT NULL,
 dentist_id INT NOT NULL,
 treatment_id INT NOT NULL,
 appointment_date DATE NOT NULL,
 appointment_time TIME NOT NULL,
 status ENUM('BOOKED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'BOOKED',
 notes VARCHAR(255),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
 FOREIGN KEY (dentist_id) REFERENCES dentists(dentist_id),
 FOREIGN KEY (treatment_id) REFERENCES treatments(treatment_id),
 UNIQUE KEY uq_dentist_slot (dentist_id, appointment_date, appointment_time)
);

CREATE TABLE IF NOT EXISTS bills (
 bill_id INT AUTO_INCREMENT PRIMARY KEY,
 appointment_id INT NOT NULL UNIQUE,
 consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
 treatment_charge DECIMAL(10,2) NOT NULL DEFAULT 0,
 total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
 payment_method VARCHAR(10) NOT NULL DEFAULT 'CASH',
 payment_status VARCHAR(20) NOT NULL DEFAULT 'PAID',
 card_last4 VARCHAR(4),
 paid_at TIMESTAMP NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);

CREATE TABLE IF NOT EXISTS help_topics (
 topic_id INT AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(150) NOT NULL,
 content TEXT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS support_tickets (
 ticket_id INT AUTO_INCREMENT PRIMARY KEY,
 created_by INT NOT NULL,
 subject VARCHAR(150) NOT NULL,
 description TEXT NOT NULL,
 priority ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
 status ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
 admin_response TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (created_by) REFERENCES users(user_id)
);

INSERT IGNORE INTO users(username,password,full_name,email,role,status) VALUES
('admin','240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9','System Administrator','admin@sunrise.local','ADMIN',1),
('reception','5145dba3b6bda2d610d2c5c435a1c2481eefd3146b6a7e004ad73f794386e031','Main Receptionist','reception@sunrise.local','RECEPTIONIST',1),
('dentist1','22990c57fbef2aeac16a2bf5e0caeafc43717c99e2040b0e3ac8d468d42794f0','Dr. Demo Dentist','dentist@sunrise.local','DENTIST',1);

INSERT IGNORE INTO dentists(dentist_id,full_name,specialization,phone,email)
VALUES(1,'Dr. Demo Dentist','General Dentistry','0770000001','dentist@sunrise.local');

INSERT IGNORE INTO treatments(treatment_id,treatment_name,description,cost) VALUES
(1,'Consultation','General dental consultation',2500.00),
(2,'Cleaning','Dental cleaning',5000.00),
(3,'Filling','Standard filling',7500.00),
(4,'Extraction','Simple extraction',10000.00);

INSERT INTO help_topics(title,content)
SELECT 'Register an appointment','Open Appointments, enter patient, dentist, treatment, date and time, then save.'
WHERE NOT EXISTS (SELECT 1 FROM help_topics WHERE title='Register an appointment');
