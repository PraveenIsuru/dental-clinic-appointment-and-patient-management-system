-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1:3306
-- Generation Time: Sep 04, 2026 at 02:21 AM
-- Server version: 8.3.0
-- PHP Version: 8.2.18

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `dental_clinic`
--

-- --------------------------------------------------------

--
-- Table structure for table `appointments`
--

DROP TABLE IF EXISTS `appointments`;
CREATE TABLE IF NOT EXISTS `appointments` (
  `appointment_id` int NOT NULL AUTO_INCREMENT,
  `appointment_no` varchar(30) NOT NULL,
  `patient_id` int NOT NULL,
  `dentist_id` int NOT NULL,
  `treatment_id` int NOT NULL,
  `appointment_date` date NOT NULL,
  `appointment_time` time NOT NULL,
  `status` enum('BOOKED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'BOOKED',
  `notes` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`appointment_id`),
  UNIQUE KEY `appointment_no` (`appointment_no`),
  UNIQUE KEY `uq_dentist_slot` (`dentist_id`,`appointment_date`,`appointment_time`),
  KEY `patient_id` (`patient_id`),
  KEY `treatment_id` (`treatment_id`)
) ENGINE=MyISAM AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `appointments`
--

INSERT INTO `appointments` (`appointment_id`, `appointment_no`, `patient_id`, `dentist_id`, `treatment_id`, `appointment_date`, `appointment_time`, `status`, `notes`, `created_at`) VALUES
(1, '1', 1, 3, 1, '2026-09-08', '10:30:00', 'BOOKED', '', '2026-09-03 20:26:09'),
(2, '2', 2, 4, 2, '2026-09-08', '10:00:00', 'BOOKED', '', '2026-09-03 20:27:31'),
(3, '3', 3, 7, 4, '2026-09-08', '13:15:00', 'BOOKED', '', '2026-09-04 02:02:17');

-- --------------------------------------------------------

--
-- Table structure for table `bills`
--

DROP TABLE IF EXISTS `bills`;
CREATE TABLE IF NOT EXISTS `bills` (
  `bill_id` int NOT NULL AUTO_INCREMENT,
  `appointment_id` int NOT NULL,
  `consultation_fee` decimal(10,2) NOT NULL DEFAULT '0.00',
  `treatment_charge` decimal(10,2) NOT NULL DEFAULT '0.00',
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `payment_method` varchar(10) NOT NULL DEFAULT 'CASH',
  `payment_status` varchar(20) NOT NULL DEFAULT 'PAID',
  `card_last4` varchar(4) DEFAULT NULL,
  `paid_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bill_id`),
  UNIQUE KEY `appointment_id` (`appointment_id`)
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `bills`
--

INSERT INTO `bills` (`bill_id`, `appointment_id`, `consultation_fee`, `treatment_charge`, `total_amount`, `payment_method`, `payment_status`, `card_last4`, `paid_at`, `created_at`) VALUES
(1, 1, 6500.00, 3000.00, 9500.00, 'CASH', 'PAID', NULL, '2026-09-03 20:28:18', '2026-09-03 20:28:18'),
(2, 3, 4000.00, 12000.00, 16000.00, 'CASH', 'PAID', NULL, '2026-09-04 02:07:22', '2026-09-04 02:07:22');

-- --------------------------------------------------------

--
-- Table structure for table `dentists`
--

DROP TABLE IF EXISTS `dentists`;
CREATE TABLE IF NOT EXISTS `dentists` (
  `dentist_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `specialization` varchar(100) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `email` varchar(120) DEFAULT NULL,
  `start_time` time NOT NULL DEFAULT '09:00:00',
  `end_time` time NOT NULL DEFAULT '17:00:00',
  PRIMARY KEY (`dentist_id`)
) ENGINE=MyISAM AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `dentists`
--

INSERT INTO `dentists` (`dentist_id`, `full_name`, `specialization`, `phone`, `email`, `start_time`, `end_time`) VALUES
(1, 'Dr. Demo Dentist', 'General Dentistry', '0770000001', 'dentist@sunrise.local', '09:00:00', '16:00:00'),
(2, 'Yohan Galappaththi', 'Orthodontists', '', 'yohangalappaththi@gmail.com', '09:00:00', '17:00:00'),
(3, 'Rohana Wanigasooriya', 'Orthodontists', '', 'rohana@gmail.com', '09:00:00', '17:00:00'),
(4, 'Nisheka Galappaththi', 'Periodontists', '', 'nisheka@gmail.com', '10:00:00', '15:00:00'),
(5, 'Yohan Abeywickrama', 'Endodontists', '', 'yohanabeywickrama@gmail.com', '09:00:00', '17:00:00'),
(6, 'Nipun Wickramasinghe', 'Pediatric Dentists', '', 'nipunw@gmail.com', '09:00:00', '17:00:00'),
(7, 'Shehara Dodamthenna', 'Orthodontists', '', 'sheharadodamthenna@gmail.com', '09:00:00', '17:00:00');

-- --------------------------------------------------------

--
-- Table structure for table `help_topics`
--

DROP TABLE IF EXISTS `help_topics`;
CREATE TABLE IF NOT EXISTS `help_topics` (
  `topic_id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(150) NOT NULL,
  `content` text NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`topic_id`)
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `help_topics`
--

INSERT INTO `help_topics` (`topic_id`, `title`, `content`, `created_at`) VALUES
(2, 'Register an New Appointment', 'Open Appointments,  Enter Patient name, Select Gender, Enter Address, Contact Number, Email and then Select Dentist, Treatment and Date and Time.', '2026-09-03 20:07:07');

-- --------------------------------------------------------

--
-- Table structure for table `patients`
--

DROP TABLE IF EXISTS `patients`;
CREATE TABLE IF NOT EXISTS `patients` (
  `patient_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(120) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `contact` varchar(30) NOT NULL,
  `email` varchar(120) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`patient_id`)
) ENGINE=MyISAM AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `patients`
--

INSERT INTO `patients` (`patient_id`, `full_name`, `gender`, `address`, `contact`, `email`, `created_at`) VALUES
(1, 'Yenali Gamlath', 'Female', 'No. 12 Medagampitiya, Gampaha', '0782968961', 'yenali@gmail.com', '2026-09-03 20:26:09'),
(2, 'Rovindu Denuka', 'Male', 'Moratuwa road Piliyandala', '0768896785', 'denuka@gmail.com', '2026-09-03 20:27:31'),
(3, 'Thomas Ravindran', 'Male', 'No.45 High level road, Nugegoda', '0772589756', 'thomr@gmail.com', '2026-09-04 01:59:50');

-- --------------------------------------------------------

--
-- Table structure for table `support_tickets`
--

DROP TABLE IF EXISTS `support_tickets`;
CREATE TABLE IF NOT EXISTS `support_tickets` (
  `ticket_id` int NOT NULL AUTO_INCREMENT,
  `created_by` int NOT NULL,
  `subject` varchar(150) NOT NULL,
  `description` text NOT NULL,
  `priority` enum('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
  `status` enum('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
  `admin_response` text,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ticket_id`),
  KEY `created_by` (`created_by`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `treatments`
--

DROP TABLE IF EXISTS `treatments`;
CREATE TABLE IF NOT EXISTS `treatments` (
  `treatment_id` int NOT NULL AUTO_INCREMENT,
  `treatment_name` varchar(120) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `cost` decimal(10,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`treatment_id`),
  UNIQUE KEY `treatment_name` (`treatment_name`)
) ENGINE=MyISAM AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `treatments`
--

INSERT INTO `treatments` (`treatment_id`, `treatment_name`, `description`, `cost`) VALUES
(1, 'Consultation', 'General dental consultation', 3000.00),
(2, 'Cleaning', 'Teeth Cleaning', 6500.00),
(3, 'Filling', 'Standard filling', 9500.00),
(4, 'Extraction', 'Simple extraction', 12000.00);

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
CREATE TABLE IF NOT EXISTS `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(120) DEFAULT NULL,
  `role` enum('ADMIN','RECEPTIONIST','DENTIST') NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=MyISAM AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`user_id`, `username`, `password`, `full_name`, `email`, `role`, `status`, `created_at`) VALUES
(1, 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'System Administrator', 'admin@sunrise.local', 'ADMIN', 1, '2026-09-03 14:20:20'),
(6, 'reception03', '82f78c8b3582521a9f2225c6ced8f059e294d03589d42c84259ef5c2dbc9e429', 'Michel Fernando', 'michel@gmail.com', 'RECEPTIONIST', 1, '2026-09-03 20:13:46'),
(4, 'reception01', '642e63d07a7df8da147145efdf5c3c9c1178f2962567253d564cf3a67fcb9f77', 'Yehara Nanayakkara', 'yeharan@gmail.com', 'RECEPTIONIST', 1, '2026-09-03 20:09:57'),
(5, 'reception02', '2d604b1c289f29f892fc539e02e46bf42b1b0d6ead2d51b6ffc4fd96ec90f60d', 'Yehani Perera', 'yehaniperera@gmail.com', 'RECEPTIONIST', 1, '2026-09-03 20:11:33'),
(7, 'reception04', 'fb6912b55a0ba56a3aa8589b924337483cb9204963679e24c360fb3dd5feef8e', 'Suneth Kavinda', 'skavinda@gmail.com', 'RECEPTIONIST', 1, '2026-09-03 20:14:05'),
(8, 'Dentist01', 'bff6ec6c33ffe5c4ff56d948af82146f17494e2fa09e5ecb93cf20cd469136cc', 'Yohan Galappaththi', 'yohangalappaththi@gmail.com', 'DENTIST', 1, '2026-09-03 20:16:21'),
(9, 'Dentist02', 'aeafd77507bfec1137ebfb7f91f817f99f089f38385090a3aa6ecc2ebff9ae4a', 'Rohana Wanigasooriya', 'rohana@gmail.com', 'DENTIST', 1, '2026-09-03 20:20:14'),
(10, 'Dentist03', '83b07e16e0fdebb98e06437d74dd1dd11f200f848ace7ccd1f67194c38b0b616', 'Nisheka Galappaththi', 'nisheka@gmail.com', 'DENTIST', 1, '2026-09-03 20:20:43'),
(11, 'Dentist04', 'cbd97704e01d55c0b46fd13d4fec1ada548afc736367d3eef78760b01962dd62', 'Yohan Abeywickrama', 'yohanabeywickrama@gmail.com', 'DENTIST', 1, '2026-09-03 20:21:13'),
(12, 'Dentist06', '6d5bad9ca724d8320c08e9a7bca28c039c46598feb2b4eb90d592b124c636795', 'Nipun Wickramasinghe', 'nipunw@gmail.com', 'DENTIST', 1, '2026-09-03 20:21:40'),
(13, 'Dentist08', '0b5b472c7c4a0a2a11c75fa3154ab1d2da15bf0f1d90f27efd1a76b318f8d135', 'Shehara Dodamthenna', 'sheharadodamthenna@gmail.com', 'DENTIST', 1, '2026-09-03 20:22:05');
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
