package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only native queries deliberately bypass model invariants to prove that
 * MariaDB constraints still protect storage. Never included in the application JAR.
 */
@Transactional
public interface IdentityConstraintProbeRepository extends Repository<HttpUserEntity, Long> {
	@Modifying
	@Query(value = "INSERT INTO http_user_role (user_id, role) VALUES (:id, :role)", nativeQuery = true)
	void insertRole(@Param("id") long id, @Param("role") String role);

	@Modifying
	@Query(value = "UPDATE http_user SET password_hash = :hash WHERE id = :id", nativeQuery = true)
	void setUserHash(@Param("id") long id, @Param("hash") String hash);

	@Modifying
	@Query(value = "UPDATE mqtt_identity SET password_hash = :hash WHERE id = :id", nativeQuery = true)
	void setMqttHash(@Param("id") long id, @Param("hash") String hash);

	@Modifying
	@Query(value = "UPDATE mqtt_identity SET device_id = :device WHERE id = :id", nativeQuery = true)
	void setDeviceId(@Param("id") long id, @Param("device") String device);

	@Modifying
	@Query(value = "INSERT INTO mqtt_grant (identity_id, topic, permission) VALUES (:id, :topic, :permission)",
			nativeQuery = true)
	void insertGrant(@Param("id") long id, @Param("topic") String topic, @Param("permission") String permission);

	@Query(value = "SELECT COUNT(*) FROM mqtt_grant WHERE identity_id = :id AND topic = :topic", nativeQuery = true)
	long countExactTopic(@Param("id") long id, @Param("topic") String topic);

	@Modifying
	@Query(value = """
			DELETE FROM mqtt_grant WHERE identity_id IN
			(SELECT id FROM mqtt_identity WHERE username LIKE :prefix)
			""", nativeQuery = true)
	void deleteGrants(@Param("prefix") String prefix);

	@Modifying
	@Query(value = "DELETE FROM mqtt_identity WHERE username LIKE :prefix", nativeQuery = true)
	void deleteMqttIdentities(@Param("prefix") String prefix);

	@Modifying
	@Query(value = """
			DELETE FROM http_user_role WHERE user_id IN
			(SELECT id FROM http_user WHERE username LIKE :prefix)
			""", nativeQuery = true)
	void deleteRoles(@Param("prefix") String prefix);

	@Modifying
	@Query(value = "DELETE FROM http_user WHERE username LIKE :prefix", nativeQuery = true)
	void deleteUsers(@Param("prefix") String prefix);
}
