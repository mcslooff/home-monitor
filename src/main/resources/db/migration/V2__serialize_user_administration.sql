-- A shared row lock serializes bootstrap and user mutations across app instances,
-- including the empty-store case where there are no user rows to lock.
CREATE TABLE user_administration_lock (
    id SMALLINT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_user_administration_lock CHECK (id = 1)
) ENGINE=InnoDB;

INSERT INTO user_administration_lock (id) VALUES (1);
