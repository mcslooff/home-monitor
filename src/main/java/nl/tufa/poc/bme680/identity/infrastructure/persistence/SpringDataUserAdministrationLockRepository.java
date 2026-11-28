package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

public interface SpringDataUserAdministrationLockRepository extends Repository<UserAdministrationLockEntity, Short> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select guard from UserAdministrationLockEntity guard where guard.id = 1")
	Optional<UserAdministrationLockEntity> acquire();
}
