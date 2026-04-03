CREATE TABLE IF NOT EXISTS t_user (
    id       BIGINT IDENTITY PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email    VARCHAR(128),
    age      INT,
    CONSTRAINT uk_username UNIQUE (username)
);

INSERT INTO t_user (username, email, age) VALUES ('alice', 'alice@example.com', 28);
INSERT INTO t_user (username, email, age) VALUES ('bob',   'bob@example.com',   32);
INSERT INTO t_user (username, email, age) VALUES ('carol', 'carol@example.com', 25);
