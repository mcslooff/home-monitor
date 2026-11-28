package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMqttGrantRepository extends JpaRepository<MqttGrantEntity, MqttGrantId> {
	List<MqttGrantEntity> findByIdIdentityIdOrderByIdTopicAscIdPermissionAsc(long identityId);
}
