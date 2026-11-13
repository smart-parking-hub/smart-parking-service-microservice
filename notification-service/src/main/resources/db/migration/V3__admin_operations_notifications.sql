ALTER TABLE notifications ADD COLUMN source_event_id VARCHAR(100) NULL;
CREATE UNIQUE INDEX uq_notification_source_recipient ON notifications (source_event_id, user_id);
CREATE INDEX idx_notification_user_created ON notifications (user_id, created_at, id);

CREATE TABLE admin_notification_recipients (
    user_id BIGINT PRIMARY KEY
);
CREATE TABLE admin_recipient_state (
    id INT PRIMARY KEY,
    snapshot_version BIGINT NOT NULL DEFAULT 0,
    source_created_at TIMESTAMP(6) NULL
);
INSERT INTO admin_recipient_state (id, snapshot_version) VALUES (1, 0);
