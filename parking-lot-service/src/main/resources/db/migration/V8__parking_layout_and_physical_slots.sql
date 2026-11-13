-- ============================================================
-- V8__parking_layout_and_physical_slots.sql
-- Parking Layout, Private File Assets, Publication Snapshot & Physical Slots
-- ============================================================

-- 1. Table: parking_layouts (metadata pointer & draft per parking lot)
CREATE TABLE IF NOT EXISTS parking_layouts (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id         BIGINT NOT NULL,
    layout_version         INT NOT NULL DEFAULT 1,
    active_publication_id VARCHAR(36) NULL,
    allocation_mode        ENUM ('GROUP', 'PHYSICAL') NOT NULL DEFAULT 'GROUP',
    draft_document         JSON NULL,
    source_asset_id        VARCHAR(36) NULL,
    created_at             DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_parking_layouts_lot UNIQUE (parking_lot_id),
    CONSTRAINT FK_parking_layouts_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: parking_layout_assets (source images & revision json records)
CREATE TABLE IF NOT EXISTS parking_layout_assets (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    asset_id           VARCHAR(36) NOT NULL,
    parking_lot_id     BIGINT NOT NULL,
    purpose            VARCHAR(30) NOT NULL,
    object_key         VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    generation         VARCHAR(64) NULL,
    checksum_crc32c    VARCHAR(64) NULL,
    width              INT NULL,
    height             INT NULL,
    created_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_layout_assets_asset_id UNIQUE (asset_id),
    CONSTRAINT UK_layout_assets_object_key UNIQUE (object_key),
    CONSTRAINT FK_layout_assets_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: parking_layout_publications (immutable snapshot publications)
CREATE TABLE IF NOT EXISTS parking_layout_publications (
    id                 VARCHAR(36) NOT NULL,
    parking_lot_id     BIGINT NOT NULL,
    layout_version     INT NOT NULL,
    storage_key        VARCHAR(255) NOT NULL,
    source_asset_id    VARCHAR(36) NULL,
    grid_rows          INT NOT NULL,
    grid_cols          INT NOT NULL,
    total_slots_count  INT NOT NULL,
    slots_summary      JSON NOT NULL,
    canonical_document JSON NULL,
    published_at       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    request_id         VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_layout_pub_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: physical_parking_slots (immutable physical slot identity per lot)
CREATE TABLE IF NOT EXISTS physical_parking_slots (
    id                 VARCHAR(36) NOT NULL,
    parking_lot_id     BIGINT NOT NULL,
    slot_group_id      BIGINT NOT NULL,
    vehicle_type       ENUM ('SEDAN_SUV','BUS_COACH','LIGHT_TRUCK','HEAVY_TRUCK','CONTAINER') NOT NULL,
    code               VARCHAR(50) NOT NULL,
    normalized_code    VARCHAR(50) NOT NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_phys_slots_lot_code UNIQUE (parking_lot_id, normalized_code),
    CONSTRAINT FK_phys_slots_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots (id) ON DELETE CASCADE,
    CONSTRAINT FK_phys_slots_group FOREIGN KEY (slot_group_id) REFERENCES slots (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: current_slot_allocations (atomic 1:1 active allocation constraint)
CREATE TABLE IF NOT EXISTS current_slot_allocations (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id     BIGINT NOT NULL,
    physical_slot_id   VARCHAR(36) NOT NULL,
    booking_id         BIGINT NOT NULL,
    slot_group_id      BIGINT NOT NULL,
    allocated_at       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_alloc_phys_slot UNIQUE (physical_slot_id),
    CONSTRAINT UK_alloc_booking UNIQUE (booking_id),
    CONSTRAINT FK_alloc_phys_slot FOREIGN KEY (physical_slot_id) REFERENCES physical_parking_slots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Alter slot_holds to add physical_slot_id and publication_id nullable
ALTER TABLE slot_holds ADD COLUMN physical_slot_id VARCHAR(36) NULL;
ALTER TABLE slot_holds ADD COLUMN publication_id VARCHAR(36) NULL;
ALTER TABLE slot_holds ADD CONSTRAINT FK_slot_holds_phys_slot FOREIGN KEY (physical_slot_id) REFERENCES physical_parking_slots (id) ON DELETE SET NULL;

-- 7. Indexes
CREATE INDEX idx_parking_layouts_lot ON parking_layouts (parking_lot_id);
CREATE INDEX idx_layout_assets_lot ON parking_layout_assets (parking_lot_id);
CREATE INDEX idx_layout_pub_lot ON parking_layout_publications (parking_lot_id);
CREATE INDEX idx_layout_pub_lot_req ON parking_layout_publications (parking_lot_id, request_id);
CREATE INDEX idx_phys_slots_lot_group ON physical_parking_slots (parking_lot_id, slot_group_id);
CREATE INDEX idx_alloc_lot ON current_slot_allocations (parking_lot_id);
