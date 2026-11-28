package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;

@Repository
@RequiredArgsConstructor
public class JpaHttpUserRepositoryAdapter implements HttpUserRepository {
	private final SpringDataHttpUserRepository users;

	@Override
	@Transactional
	public HttpUser save(HttpUser user) {
		return IdentityPersistenceMapper.toDomain(users.saveAndFlush(IdentityPersistenceMapper.toEntity(user)));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<HttpUser> findByUsername(String username) {
		return users.findByUsername(username).map(IdentityPersistenceMapper::toDomain);
	}
}
