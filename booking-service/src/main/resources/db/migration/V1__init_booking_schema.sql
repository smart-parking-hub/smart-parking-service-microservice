-- ===================================================
-- SMART PARKING BOOKING SERVICE SCHEMA
-- ===================================================

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(50) NOT NULL UNIQUE,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    parking_lot_id BIGINT NOT NULL,
    merchant_id BIGINT,
    vehicle_type VARCHAR(30) NOT NULL,
    vehicle_plate VARCHAR(30),
    parking_lot_name VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    advance_deposit DECIMAL(10, 2),
    total_amount DECIMAL(10, 2),
    expected_check_in_time TIMESTAMP NULL,
    expected_check_out_time TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    check_in_time TIMESTAMP NULL,
    check_out_time TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bookings_driver (driver_id),
    INDEX idx_bookings_lot (parking_lot_id),
    INDEX idx_bookings_merchant (merchant_id),
    INDEX idx_bookings_status (status),
    INDEX idx_bookings_code (booking_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    parking_lot_id BIGINT NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_deadline TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_queues_driver (driver_id),
    INDEX idx_queues_lot (parking_lot_id),
    INDEX idx_queues_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    parking_lot_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reviews_lot (parking_lot_id),
    INDEX idx_reviews_driver (driver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Transactional Outbox Pattern Table
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    INDEX idx_booking_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
