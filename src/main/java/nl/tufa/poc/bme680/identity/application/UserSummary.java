package nl.tufa.poc.bme680.identity.application;

import java.util.Set;

import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;

/** Safe use-case output: deliberately contains no credential material. */
public record UserSummary(long id, String username, boolean enabled, Set<Role> roles) {
	public UserSummary {
		roles = Set.copyOf(roles);
	}

	static UserSummary from(HttpUser user) {
		return new UserSummary(user.id(), user.username(), user.enabled(), user.roles());
	}
}
