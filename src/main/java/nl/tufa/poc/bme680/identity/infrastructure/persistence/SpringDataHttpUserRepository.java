package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;

public interface SpringDataHttpUserRepository extends JpaRepository<HttpUserEntity, Long> {
	Optional<HttpUserEntity> findByUsername(String username);

	@EntityGraph(attributePaths = "roles")
	List<HttpUserEntity> findAllByOrderByIdAsc();

	@Query("select count(distinct u.id) from HttpUserEntity u join u.roles role where u.enabled = true and role = :role")
	long countEnabledWithRole(@Param("role") Role role);
}
