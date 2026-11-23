CREATE TABLE http_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_http_username UNIQUE (username),
    CONSTRAINT ck_http_username CHECK (
        CHAR_LENGTH(username) BETWEEN 1 AND 64 AND username NOT REGEXP '[^A-Za-z0-9_.@-]'
    ),
    CONSTRAINT ck_http_password_hash CHECK (
        OCTET_LENGTH(password_hash) = 68
            AND password_hash REGEXP '^[{]bcrypt[}][$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$'
    ),
    CONSTRAINT ck_http_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE http_user_role (
    user_id BIGINT NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_role_user FOREIGN KEY (user_id) REFERENCES http_user (id),
    CONSTRAINT ck_http_role CHECK (role IN ('USER', 'ADMIN', 'SENSOR'))
) ENGINE=InnoDB;

CREATE TABLE mqtt_identity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    principal_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    device_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_nopad_bin,
    CONSTRAINT uq_mqtt_username UNIQUE (username),
    CONSTRAINT uq_mqtt_device UNIQUE (device_id),
    CONSTRAINT ck_mqtt_username CHECK (
        CHAR_LENGTH(username) BETWEEN 1 AND 64 AND username NOT REGEXP '[^A-Za-z0-9_.@-]'
    ),
    CONSTRAINT ck_mqtt_password_hash CHECK (
        OCTET_LENGTH(password_hash) = 68
            AND password_hash REGEXP '^[{]bcrypt[}][$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$'
    ),
    CONSTRAINT ck_mqtt_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_mqtt_principal CHECK (
        (principal_kind = 'DEVICE' AND device_id IS NOT NULL
            AND CHAR_LENGTH(device_id) BETWEEN 1 AND 64 AND device_id NOT REGEXP '[^A-Za-z0-9_-]')
        OR (principal_kind = 'SERVICE' AND device_id IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE mqtt_grant (
    identity_id BIGINT NOT NULL,
    topic VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_nopad_bin NOT NULL,
    permission VARCHAR(16) CHARACTER SET ascii COLLATE ascii_nopad_bin NOT NULL,
    PRIMARY KEY (identity_id, topic, permission),
    CONSTRAINT fk_grant_identity FOREIGN KEY (identity_id) REFERENCES mqtt_identity (id),
    CONSTRAINT ck_mqtt_topic CHECK (
        CHAR_LENGTH(topic) > 0 AND topic NOT LIKE '%#%' AND topic NOT LIKE '%+%'
            AND topic NOT LIKE '$%'
    ),
    CONSTRAINT ck_mqtt_permission CHECK (permission IN ('PUBLISH', 'SUBSCRIBE'))
) ENGINE=InnoDB;
