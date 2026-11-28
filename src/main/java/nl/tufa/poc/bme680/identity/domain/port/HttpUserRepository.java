package nl.tufa.poc.bme680.identity.domain.port;

import java.util.Optional;
import java.util.List;

import nl.tufa.poc.bme680.identity.domain.model.HttpUser;

/**
 * Storage boundary for identity consumers. Implementations must propagate storage
 * failures, never interpret them as missing users or substitute demo accounts.
 */
public interface HttpUserRepository {
	HttpUser save(HttpUser user);

	Optional<HttpUser> findByUsername(String username);

	Optional<HttpUser> findById(long id);

	List<HttpUser> findAll();

	long count();

	long countEnabledAdministrators();
}
