-- Device/service creation must see one another's committed state when assigning
-- exact subscriptions. Rotation and enabled-state updates share the same lock.
CREATE TABLE mqtt_administration_lock (
    id SMALLINT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_mqtt_administration_lock CHECK (id = 1)
) ENGINE=InnoDB;

INSERT INTO mqtt_administration_lock (id) VALUES (1);
