package nl.tufa.poc.bme680.identity.domain.model;

public record MqttGrant(long identityId, String topic, Permission permission) {

	public enum Permission {
		PUBLISH, SUBSCRIBE
	}
}
