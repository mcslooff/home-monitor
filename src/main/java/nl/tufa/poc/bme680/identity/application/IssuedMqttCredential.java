package nl.tufa.poc.bme680.identity.application;

/** The secret is returned only by authenticated creation/rotation, never stored. */
public record IssuedMqttCredential(MqttIdentitySummary identity, String secret) {
	@Override
	public String toString() {
		return "IssuedMqttCredential[identity=" + identity + ", secret=REDACTED]";
	}
}
