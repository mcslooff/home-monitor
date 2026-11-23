package nl.tufa.poc.bme680.identity;

public record MqttIdentity(long id, String username, PasswordHash passwordHash, boolean enabled,
		Kind kind, String deviceId) {

	public enum Kind {
		DEVICE, SERVICE
	}
}
