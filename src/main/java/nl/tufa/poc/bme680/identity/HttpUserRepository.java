package nl.tufa.poc.bme680.identity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.HttpUser.Role;

@Repository
@RequiredArgsConstructor
public class HttpUserRepository {

	private final JdbcClient jdbc;

	@Transactional
	public HttpUser create(String username, PasswordHash passwordHash, boolean enabled, Set<Role> roles) {
		long id = jdbc.sql("""
				INSERT INTO http_user (username, password_hash, enabled)
				VALUES (:username, :hash, :enabled) RETURNING id
				""").param("username", username).param("hash", passwordHash.encoded())
				.param("enabled", enabled).query(Long.class).single();
		for (Role role : roles) {
			jdbc.sql("INSERT INTO http_user_role (user_id, role) VALUES (:id, :role)")
					.param("id", id).param("role", role.name()).update();
		}
		return new HttpUser(id, username, passwordHash, enabled, roles);
	}

	@Transactional(readOnly = true)
	public Optional<HttpUser> findByUsername(String username) {
		return jdbc.sql("SELECT id, username, password_hash, enabled FROM http_user WHERE username = :username")
				.param("username", username)
				.query((rs, row) -> new HttpUser(rs.getLong("id"), rs.getString("username"),
						new PasswordHash(rs.getString("password_hash")), rs.getBoolean("enabled"), Set.of()))
				.optional().map(user -> new HttpUser(user.id(), user.username(), user.passwordHash(), user.enabled(),
						new HashSet<>(jdbc.sql("SELECT role FROM http_user_role WHERE user_id = :id")
								.param("id", user.id()).query((rs, row) -> Role.valueOf(rs.getString("role"))).list())));
	}
}
