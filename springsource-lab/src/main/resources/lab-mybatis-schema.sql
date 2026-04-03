-- W92 MyBatis-Spring 集成练兵场 Schema
CREATE TABLE IF NOT EXISTS t_order (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no   VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    amount     BIGINT       NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO t_order (order_no, user_id, amount, status) VALUES
('ORD-2026-001', 1001, 9900, 'CREATED'),
('ORD-2026-002', 1001, 5600, 'PAID'),
('ORD-2026-003', 1002, 12800, 'CREATED');
