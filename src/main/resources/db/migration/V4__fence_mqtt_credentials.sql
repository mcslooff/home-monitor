ALTER TABLE mqtt_identity
    ADD COLUMN credential_generation BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_mqtt_credential_generation CHECK (credential_generation > 0);
