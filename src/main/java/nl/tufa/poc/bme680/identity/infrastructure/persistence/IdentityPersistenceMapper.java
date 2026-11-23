package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.HashSet;

import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;

final class IdentityPersistenceMapper {
	private IdentityPersistenceMapper() {
	}

	static HttpUserEntity toEntity(HttpUser user) {
		return new HttpUserEntity(user.id(), user.username(), user.passwordHash().encoded(),
				user.enabled(), new HashSet<>(user.roles()));
	}

	static HttpUser toDomain(HttpUserEntity user) {
		return new HttpUser(user.getId(), user.getUsername(), new PasswordHash(user.getPasswordHash()),
				user.isEnabled(), user.getRoles());
	}

	static MqttIdentityEntity toEntity(MqttIdentity identity) {
		return new MqttIdentityEntity(identity.id(), identity.username(), identity.passwordHash().encoded(),
				identity.enabled(), identity.kind(), identity.deviceId());
	}

	static MqttIdentity toDomain(MqttIdentityEntity identity) {
		return new MqttIdentity(identity.getId(), identity.getUsername(), new PasswordHash(identity.getPasswordHash()),
				identity.isEnabled(), identity.getKind(), identity.getDeviceId());
	}

	static MqttGrantEntity toEntity(MqttGrant grant) {
		return new MqttGrantEntity(new MqttGrantId(grant.identityId(), grant.topic(), grant.permission()));
	}

	static MqttGrant toDomain(MqttGrantEntity grant) {
		return new MqttGrant(grant.getId().getIdentityId(), grant.getId().getTopic(), grant.getId().getPermission());
	}
}
