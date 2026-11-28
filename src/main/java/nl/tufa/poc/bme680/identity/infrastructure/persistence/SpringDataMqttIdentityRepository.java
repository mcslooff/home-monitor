package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMqttIdentityRepository extends JpaRepository<MqttIdentityEntity, Long> {
	Optional<MqttIdentityEntity> findByUsername(String username);
}
