-- Payment owns both tables. Keep every historical payment/order unchanged.
CREATE TABLE payment_attempt_groups (
    group_key VARCHAR(100) PRIMARY KEY
);

CREATE TABLE payment_order_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
);

-- Start allocations beyond every order already issued, including legacy/demo orders.
INSERT INTO payment_order_codes (id)
SELECT COALESCE(MAX(order_code), 0) + 1 FROM payments;

INSERT INTO payment_attempt_groups (group_key)
SELECT DISTINCT CONCAT('booking:', booking_id, ':', payment_type) FROM payments;
