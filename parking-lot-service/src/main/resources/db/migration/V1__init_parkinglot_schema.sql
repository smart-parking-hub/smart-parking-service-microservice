-- ============================================================
-- V1__init_parkinglot_schema.sql
-- Parking Lot, Slot & Pricing Catalog Schema (Database-per-Service)
-- ============================================================

-- 1. Table: parking_lots
CREATE TABLE IF NOT EXISTS parking_lots (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id    BIGINT NOT NULL,
    name           VARCHAR(150) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    latitude       DECIMAL(10,8) NOT NULL,
    longitude      DECIMAL(11,8) NOT NULL,
    status         ENUM ('PENDING_APPROVAL','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING_APPROVAL',
    rating_average DECIMAL(3,2) DEFAULT 0.00,
    review_reason  VARCHAR(1000),
    reviewed_by    BIGINT,
    reviewed_at    DATETIME(6),
    created_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: slots (capacity bucket per vehicle type)
CREATE TABLE IF NOT EXISTS slots (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id     BIGINT NOT NULL,
    vehicle_type       ENUM ('SEDAN_SUV','BUS_COACH','LIGHT_TRUCK','HEAVY_TRUCK','CONTAINER') NOT NULL,
    total_capacity     INT NOT NULL DEFAULT 0,
    available_capacity INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT UK_slots_lot_vehicle UNIQUE (parking_lot_id, vehicle_type),
    CONSTRAINT FK_slots_parking_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: pricing_rules
CREATE TABLE IF NOT EXISTS pricing_rules (
    id                          BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id              BIGINT NOT NULL,
    vehicle_type                ENUM ('SEDAN_SUV','BUS_COACH','LIGHT_TRUCK','HEAVY_TRUCK','CONTAINER') NOT NULL,
    base_price_per_hour         DECIMAL(10,2) NOT NULL,
    surge_multiplier            DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    surge_start_time            TIME,
    surge_end_time              TIME,
    overtime_penalty_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.50,
    PRIMARY KEY (id),
    CONSTRAINT UK_pricing_lot_vehicle UNIQUE (parking_lot_id, vehicle_type),
    CONSTRAINT FK_pricing_parking_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: parking_lot_images
CREATE TABLE IF NOT EXISTS parking_lot_images (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id     BIGINT NOT NULL,
    stored_file_name   VARCHAR(100) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type       VARCHAR(30)  NOT NULL,
    size_bytes         BIGINT NOT NULL,
    is_cover           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_lot_images_stored UNIQUE (stored_file_name),
    CONSTRAINT FK_images_parking_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for search and merchant queries
CREATE INDEX idx_parking_lots_merchant ON parking_lots (merchant_id);
CREATE INDEX idx_parking_lots_status ON parking_lots (status);
CREATE INDEX idx_parking_lots_lat_lon ON parking_lots (latitude, longitude);
CREATE INDEX idx_slots_parking_lot ON slots (parking_lot_id);
CREATE INDEX idx_pricing_parking_lot ON pricing_rules (parking_lot_id);
CREATE INDEX idx_images_parking_lot ON parking_lot_images (parking_lot_id);
