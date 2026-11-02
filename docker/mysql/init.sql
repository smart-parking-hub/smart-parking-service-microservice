-- ============================================================
-- SmartParkingHub MySQL Database Initialization Script
-- Auto-executed by MySQL Docker container on first boot
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_parking_identity_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_parking_lot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_parking_booking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_parking_payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_parking_notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant all privileges to root
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- ============================================================
-- Least-Privilege Microservice Users (No DROP permission)
-- ============================================================

-- 1. Identity Service User
CREATE USER IF NOT EXISTS 'identity_svc'@'%' IDENTIFIED BY 'SP_SecUser_Iden2026_xM3!';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'identity_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, EXECUTE ON smart_parking_identity_db.* TO 'identity_svc'@'%';

-- 2. Parking Lot Service User
CREATE USER IF NOT EXISTS 'parking_svc'@'%' IDENTIFIED BY 'SP_SecUser_Park2026_vT8!';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'parking_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, EXECUTE ON smart_parking_lot_db.* TO 'parking_svc'@'%';

-- 3. Booking Service User
CREATE USER IF NOT EXISTS 'booking_svc'@'%' IDENTIFIED BY 'SP_SecUser_Book2026_wR5!';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'booking_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, EXECUTE ON smart_parking_booking_db.* TO 'booking_svc'@'%';

-- 4. Payment Service User
CREATE USER IF NOT EXISTS 'payment_svc'@'%' IDENTIFIED BY 'SP_SecUser_Pay2026_kP7!';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'payment_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, EXECUTE ON smart_parking_payment_db.* TO 'payment_svc'@'%';

-- 5. Notification Service User
CREATE USER IF NOT EXISTS 'notif_svc'@'%' IDENTIFIED BY 'SP_SecUser_Notif2026_jQ9!';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'notif_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, EXECUTE ON smart_parking_notification_db.* TO 'notif_svc'@'%';

FLUSH PRIVILEGES;
