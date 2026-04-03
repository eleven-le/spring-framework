-- W48 缓存与事务一致性 实验表
CREATE TABLE IF NOT EXISTS product (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price INT NOT NULL
);

CREATE TABLE IF NOT EXISTS stock (
    product_id BIGINT PRIMARY KEY,
    quantity   INT NOT NULL
);
