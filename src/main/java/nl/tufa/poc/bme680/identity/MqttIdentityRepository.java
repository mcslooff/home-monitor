package nl.tufa.poc.bme680.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.MqttIdentity.Kind;

@Repository
@RequiredArgsConstructor
public class MqttIdentityRepository {

	private final JdbcClient jdbc;

	public MqttIdentity create(String username, PasswordHash passwordHash, boolean enabled, Kind kind, String deviceId) {
		long id = jdbc.sql("""
				INSERT INTO mqtt_identity (username, password_hash, enabled, principal_kind, device_id)
				VALUES (:username, :hash, :enabled, :kind, :device) RETURNING id
				""").param("username", username).param("hash", passwordHash.encoded()).param("enabled", enabled)
				.param("kind", kind.name()).param("device", deviceId, java.sql.Types.VARCHAR).query(Long.class).single();
		return new MqttIdentity(id, username, passwordHash, enabled, kind, deviceId);
	}

	public Optional<MqttIdentity> findByUsername(String username) {
		return jdbc.sql("SELECT * FROM mqtt_identity WHERE username = :username").param("username", username)
				.query((rs, row) -> new MqttIdentity(rs.getLong("id"), rs.getString("username"),
						new PasswordHash(rs.getString("password_hash")), rs.getBoolean("enabled"),
						Kind.valueOf(rs.getString("principal_kind")), rs.getString("device_id")))
				.optional();
	}

	public void addGrant(MqttGrant grant) {
		jdbc.sql("INSERT INTO mqtt_grant (identity_id, topic, permission) VALUES (:id, :topic, :permission)")
				.param("id", grant.identityId()).param("topic", grant.topic())
				.param("permission", grant.permission().name()).update();
	}

	public List<MqttGrant> findGrants(long identityId) {
		return jdbc.sql("SELECT topic, permission FROM mqtt_grant WHERE identity_id = :id ORDER BY topic, permission")
				.param("id", identityId)
				.query((rs, row) -> new MqttGrant(identityId, rs.getString("topic"),
						Permission.valueOf(rs.getString("permission")))).list();
	}
}
