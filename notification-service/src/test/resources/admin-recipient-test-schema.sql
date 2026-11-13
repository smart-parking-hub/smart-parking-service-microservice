CREATE TABLE IF NOT EXISTS admin_notification_recipients (user_id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS admin_recipient_state (
 id INT PRIMARY KEY, snapshot_version BIGINT NOT NULL DEFAULT 0, source_created_at TIMESTAMP(6)
);
DELETE FROM admin_notification_recipients;
DELETE FROM admin_recipient_state;
INSERT INTO admin_recipient_state (id, snapshot_version) VALUES (1, 0);
