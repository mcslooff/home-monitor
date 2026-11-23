package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

@Repository
@RequiredArgsConstructor
public class JpaMqttIdentityRepositoryAdapter implements MqttIdentityRepository {
	private final SpringDataMqttIdentityRepository identities;
	private final SpringDataMqttGrantRepository grants;

	@Override
	@Transactional
	public MqttIdentity save(MqttIdentity identity) {
		return IdentityPersistenceMapper.toDomain(identities.saveAndFlush(IdentityPersistenceMapper.toEntity(identity)));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<MqttIdentity> findByUsername(String username) {
		return identities.findByUsername(username).map(IdentityPersistenceMapper::toDomain);
	}

	@Override
	@Transactional
	public void addGrant(MqttGrant grant) {
		grants.saveAndFlush(IdentityPersistenceMapper.toEntity(grant));
	}

	@Override
	@Transactional(readOnly = true)
	public List<MqttGrant> findGrants(long identityId) {
		return grants.findByIdIdentityIdOrderByIdTopicAscIdPermissionAsc(identityId)
				.stream().map(IdentityPersistenceMapper::toDomain).toList();
	}
}
