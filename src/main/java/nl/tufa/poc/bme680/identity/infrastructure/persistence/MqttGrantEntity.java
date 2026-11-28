package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mqtt_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class MqttGrantEntity implements Persistable<MqttGrantId> {
	@EmbeddedId
	private MqttGrantId id;

	@Override
	@Transient
	public boolean isNew() {
		// Grants are insert-only: an existing key must fail, not silently merge.
		return true;
	}
}
