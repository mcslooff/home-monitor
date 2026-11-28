package nl.tufa.poc.bme680.identity.application;

import nl.tufa.poc.bme680.identity.application.port.PasswordVerifier;
import nl.tufa.poc.bme680.identity.domain.model.MqttClientId;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

/** Storage failures propagate to the broker adapter, which must deny access. No cached authorization state. */
public class MqttAuthenticationService {
	private final MqttIdentityRepository identities;
	private final PasswordVerifier passwords;

	public MqttAuthenticationService(MqttIdentityRepository identities, PasswordVerifier passwords) {
		this.identities = identities;
		this.passwords = passwords;
	}

	public boolean authenticate(String clientId, String username, String password) {
		var parsed = MqttClientId.parse(clientId);
		if (parsed.isEmpty() || !parsed.get().username().equals(username) || password == null) {
			return false;
		}
		return identities.findByUsername(username).filter(identity ->
				identity.enabled() && parsed.get().belongsTo(identity)
						&& passwords.matches(password, identity.passwordHash())).isPresent();
	}

	public boolean isCurrentSession(String clientId, String username) {
		var parsed = MqttClientId.parse(clientId);
		if (parsed.isEmpty() || !parsed.get().username().equals(username)) {
			return false;
		}
		return identities.findByUsername(username)
				.filter(identity -> identity.enabled() && parsed.get().belongsTo(identity)).isPresent();
	}
}
