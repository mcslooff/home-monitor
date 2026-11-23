package nl.tufa.poc.bme680.identity.domain.model;

import java.util.Set;

public record HttpUser(Long id, String username, PasswordHash passwordHash, boolean enabled, Set<Role> roles) {

	public HttpUser {
		roles = Set.copyOf(roles);
	}

	public enum Role {
		USER, ADMIN, SENSOR
	}
}
