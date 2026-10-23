-- Parking Lot & Slot Catalog Schema
CREATE TABLE IF NOT EXISTS parking_lots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    total_capacity INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS parking_lot_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_lot_image FOREIGN KEY (parking_lot_id) REFERENCES parking_lots(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL,
    slot_code VARCHAR(50) NOT NULL,
    slot_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    floor_level VARCHAR(20) DEFAULT '1',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_lot_slot UNIQUE (parking_lot_id, slot_code),
    CONSTRAINT fk_slot_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pricing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL DEFAULT 'CAR',
    base_hourly_rate DECIMAL(12, 2) NOT NULL,
    peak_hour_rate DECIMAL(12, 2),
    overtime_multiplier DECIMAL(4, 2) DEFAULT 1.5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots(id) ON DELETE CASCADE
);
