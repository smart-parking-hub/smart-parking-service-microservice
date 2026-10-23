-- ============================================================
-- V1__init_identity_schema.sql
-- Identity & Access Management Schema (Database-per-Service)
-- ============================================================

-- 1. Table: users
CREATE TABLE IF NOT EXISTS users (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    phone             VARCHAR(15)  NOT NULL,
    email             VARCHAR(100),
    full_name         VARCHAR(100),
    password_hash     VARCHAR(255) NOT NULL,
    role              ENUM ('DRIVER','MERCHANT','ADMIN') NOT NULL,
    status            ENUM ('ACTIVE','INACTIVE','BANNED') NOT NULL DEFAULT 'ACTIVE',
    emergency_contact VARCHAR(20),
    address           VARCHAR(255),
    status_reason     VARCHAR(1000),
    created_at        DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_users_phone UNIQUE (phone),
    CONSTRAINT UK_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: drivers
CREATE TABLE IF NOT EXISTS drivers (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    user_id        BIGINT NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    rating_average DECIMAL(3,2) DEFAULT 5.00,
    created_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_drivers_user_id UNIQUE (user_id),
    CONSTRAINT FK_drivers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: merchants
CREATE TABLE IF NOT EXISTS merchants (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    citizen_id       VARCHAR(12)  NOT NULL,
    business_license VARCHAR(50),
    bank_name        VARCHAR(100) NOT NULL,
    bank_account_num VARCHAR(30)  NOT NULL,
    wallet_balance   DECIMAL(15,2) DEFAULT 0.00,
    created_at       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_merchants_user_id UNIQUE (user_id),
    CONSTRAINT UK_merchants_citizen_id UNIQUE (citizen_id),
    CONSTRAINT FK_merchants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: vehicles
CREATE TABLE IF NOT EXISTS vehicles (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    driver_id     BIGINT NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    vehicle_type  ENUM ('SEDAN_SUV','BUS_COACH','LIGHT_TRUCK','HEAVY_TRUCK','CONTAINER') NOT NULL,
    weight_ton    DECIMAL(5,2),
    brand_model   VARCHAR(100),
    status        ENUM ('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_vehicles_license_plate UNIQUE (license_plate),
    CONSTRAINT FK_vehicles_driver FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: otp_challenges
CREATE TABLE IF NOT EXISTS otp_challenges (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    phone         VARCHAR(20) NOT NULL,
    purpose       ENUM ('REGISTER','RESET_PASSWORD','LOGIN') NOT NULL,
    code_hash     VARCHAR(100) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    consumed_at   DATETIME(6),
    attempt_count INT NOT NULL DEFAULT 0,
    created_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table: audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT,
    target_id     BIGINT,
    target_type   VARCHAR(50)  NOT NULL,
    action        VARCHAR(100) NOT NULL,
    detail        VARCHAR(1000),
    result        VARCHAR(20),
    created_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for performance
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_vehicles_driver_id ON vehicles (driver_id);
CREATE INDEX idx_otp_phone_purpose ON otp_challenges (phone, purpose);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id);
