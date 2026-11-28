package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

public interface SpringDataMqttAdministrationLockRepository extends Repository<MqttAdministrationLockEntity, Short> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select guard from MqttAdministrationLockEntity guard where guard.id = 1")
	Optional<MqttAdministrationLockEntity> acquire();
}
