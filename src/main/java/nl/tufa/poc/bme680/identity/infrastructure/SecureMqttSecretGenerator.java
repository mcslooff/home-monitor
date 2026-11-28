package nl.tufa.poc.bme680.identity.infrastructure;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import nl.tufa.poc.bme680.identity.application.port.MqttSecretGenerator;

@Component
public class SecureMqttSecretGenerator implements MqttSecretGenerator {
	private final SecureRandom random = new SecureRandom();

	@Override
	public String generate() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
