-- Keep existing enum values in their original order and preserve the default.
ALTER TABLE parking_lots
    MODIFY COLUMN status ENUM (
        'PENDING_APPROVAL',
        'APPROVED',
        'REJECTED',
        'SUSPENDED',
        'CHANGES_REQUESTED'
    ) NOT NULL DEFAULT 'PENDING_APPROVAL';
