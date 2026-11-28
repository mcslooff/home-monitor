package nl.tufa.poc.bme680.identity.domain.model;

public record MqttIdentity(Long id, String username, PasswordHash passwordHash, boolean enabled,
		Kind kind, String deviceId) {

	public enum Kind {
		DEVICE, SERVICE
	}
}
