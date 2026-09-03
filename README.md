# Sunrise Dental Clinic Appointment & Patient Management System

A Java desktop application for managing a dental clinic's day-to-day operations: patients, appointments, billing, dentists, treatments and reporting.

## Technology
- Java 21
- JavaFX + FXML
- Maven
- JDBC
- MySQL / MAMP (port 8889)
- JUnit 5

## Features
- Login and credential validation
- ADMIN / RECEPTIONIST / DENTIST roles
- User management
- Patient management
- Dentist and treatment data
- Register/search/delete appointments
- Dentist availability checking
- Billing and treatment charge calculation
- Receipt generation and JavaFX printing
- Help topics
- Summary reports with a downloadable PDF
- JUnit tests

## Design
A minimal, single-accent interface (navy on a light neutral background, underline-style form fields, no logo or imagery) built entirely with JavaFX FXML and CSS (`clinic.css`) — no external UI toolkit.

## Setup
1. Start MySQL.
2. Import `db/dental_clinic.sql` (e.g. via phpMyAdmin or the `mysql` CLI).
3. Open this folder as a Maven project in NetBeans.
4. Run Maven goal `javafx:run`.

Default connection in `DBConnection.java`:
- Host: localhost
- Port: 3306
- Database: dental_clinic
- User: root
- Password: (none)

Demo accounts:
- admin / admin123
- reception / reception123
- dentist1 / dentist123
