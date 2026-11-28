package nl.tufa.poc.bme680.identity.domain.model;

public record MqttIdentity(Long id, String username, PasswordHash passwordHash, boolean enabled,
		Kind kind, String deviceId, long credentialGeneration) {

	public MqttIdentity {
		if (credentialGeneration < 1) {
			throw new IllegalArgumentException("Credential generation must be positive");
		}
	}

	public MqttIdentity(Long id, String username, PasswordHash passwordHash, boolean enabled, Kind kind, String deviceId) {
		this(id, username, passwordHash, enabled, kind, deviceId, 1);
	}

	public enum Kind {
		DEVICE, SERVICE
	}
}
