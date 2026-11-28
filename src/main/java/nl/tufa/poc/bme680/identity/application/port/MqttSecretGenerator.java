package nl.tufa.poc.bme680.identity.application.port;

public interface MqttSecretGenerator {
	/** Generate 32 cryptographically random bytes encoded as 43 unpadded Base64URL characters. */
	String generate();
}
