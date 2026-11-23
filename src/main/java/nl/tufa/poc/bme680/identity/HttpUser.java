package nl.tufa.poc.bme680.identity;

import java.util.Set;

public record HttpUser(long id, String username, PasswordHash passwordHash, boolean enabled, Set<Role> roles) {

	public HttpUser {
		roles = Set.copyOf(roles);
	}

	public enum Role {
		USER, ADMIN, SENSOR
	}
}
