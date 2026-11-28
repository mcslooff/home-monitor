package nl.tufa.poc.bme680.identity.application;

import nl.tufa.poc.bme680.identity.domain.model.MqttClientId;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

/** Exact grants only; no wildcard inference or cached permission state. */
public class MqttAuthorizationService {
	private final MqttIdentityRepository identities;

	public MqttAuthorizationService(MqttIdentityRepository identities) {
		this.identities = identities;
	}

	public boolean permits(String username, String clientId, String topic, Permission permission) {
		if (username == null || permission == null || !isMeasurementTopic(topic)) {
			return false;
		}
		var parsed = MqttClientId.parse(clientId);
		if (parsed.isEmpty() || !parsed.get().username().equals(username)) {
			return false;
		}
		var found = identities.findByUsername(username);
		if (found.isEmpty()) {
			return false;
		}
		var identity = found.get();
		if (!identity.enabled() || !parsed.get().belongsTo(identity)) {
			return false;
		}
		if (identity.kind() == Kind.DEVICE && (permission != Permission.PUBLISH
				|| !topic.equals("sensors/" + identity.deviceId() + "/measur"))) {
			return false;
		}
		return identities.findGrants(identity.id()).stream()
				.anyMatch(grant -> grant.identityId() == identity.id()
						&& grant.permission() == permission && topic.equals(grant.topic()));
	}

	public static boolean isMeasurementTopic(String topic) {
		return topic != null && topic.matches("sensors/[A-Za-z0-9_-]{1,64}/measur");
	}
}
