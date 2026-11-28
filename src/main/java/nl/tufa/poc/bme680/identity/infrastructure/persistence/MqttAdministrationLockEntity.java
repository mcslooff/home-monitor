package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mqtt_administration_lock")
public class MqttAdministrationLockEntity {
	@Id
	private Short id;

	protected MqttAdministrationLockEntity() {
	}
}
