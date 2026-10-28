CREATE TABLE IF NOT EXISTS `user` (
 id BIGINT PRIMARY KEY, username VARCHAR(50), password VARCHAR(255), email VARCHAR(100),
 phone VARCHAR(30), avatar VARCHAR(255), score INT, status INT,
 create_time TIMESTAMP, update_time TIMESTAMP
);
CREATE TABLE IF NOT EXISTS admin (
 id BIGINT PRIMARY KEY, username VARCHAR(50), password VARCHAR(255), real_name VARCHAR(50),
 avatar VARCHAR(255), status INT, last_login_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP
);
