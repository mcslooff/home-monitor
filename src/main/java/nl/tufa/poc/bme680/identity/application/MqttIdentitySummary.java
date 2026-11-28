package nl.tufa.poc.bme680.identity.application;

import java.util.List;

import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;

/** Safe metadata: neither the password hash nor the issued secret belongs here. */
public record MqttIdentitySummary(long id, String username, boolean enabled, Kind kind,
		String deviceId, List<MqttGrant> grants) {
	public MqttIdentitySummary {
		grants = List.copyOf(grants);
	}

	static MqttIdentitySummary from(MqttIdentity identity, List<MqttGrant> grants) {
		return new MqttIdentitySummary(identity.id(), identity.username(), identity.enabled(),
				identity.kind(), identity.deviceId(), grants);
	}
}
