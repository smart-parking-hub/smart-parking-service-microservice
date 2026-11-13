CREATE TABLE IF NOT EXISTS admin_event_outbox (
 event_id VARCHAR(100) PRIMARY KEY, topic_name VARCHAR(100) NOT NULL,
 partition_key VARCHAR(100) NOT NULL, payload LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, next_attempt_at TIMESTAMP(6) NOT NULL, published_at TIMESTAMP(6)
);
CREATE TABLE IF NOT EXISTS admin_recipient_snapshot_cursor (
 id INT PRIMARY KEY, snapshot_version BIGINT NOT NULL DEFAULT 0,
 fingerprint VARCHAR(64) NOT NULL DEFAULT '', captured_at TIMESTAMP(6)
);
INSERT INTO admin_recipient_snapshot_cursor (id, snapshot_version, fingerprint)
 SELECT 1, 0, '' WHERE NOT EXISTS (SELECT 1 FROM admin_recipient_snapshot_cursor WHERE id = 1);
