package nl.tufa.poc.bme680.identity.application.port;

import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;

public interface PasswordVerifier {
	boolean matches(String rawPassword, PasswordHash hash);
}
