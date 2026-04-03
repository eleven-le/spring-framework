CREATE TABLE IF NOT EXISTS t_order (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    product VARCHAR(64),
    amount  INT
);
