package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataHttpUserRepository extends JpaRepository<HttpUserEntity, Long> {
	Optional<HttpUserEntity> findByUsername(String username);
}
