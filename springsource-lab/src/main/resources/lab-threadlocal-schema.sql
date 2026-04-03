-- W49 ThreadLocal 边界与上下文传播练兵场 Schema
CREATE TABLE IF NOT EXISTS orders (
    id         IDENTITY PRIMARY KEY,
    order_id   VARCHAR(64) NOT NULL,
    amount     DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
