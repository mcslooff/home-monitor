package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import nl.tufa.poc.bme680.DatabaseFixture;
import nl.tufa.poc.bme680.identity.HttpUser.Role;
import nl.tufa.poc.bme680.identity.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.MqttIdentity.Kind;
import nl.tufa.poc.bme680.security.WebSecurity;

class IdentityStoreIT {

	private static DatabaseFixture database;
	private static PasswordHash hash;

	@BeforeAll
	static void migrateEmptyDatabase() {
		database = DatabaseFixture.open();
		database.flyway().migrate();
		hash = new PasswordHash(new WebSecurity().passwordEncoder().encode("test-only-secret"));
	}

	@AfterAll
	static void stopDatabase() {
		if (database != null) {
			database.close();
		}
	}

	@Test
	void identitiesRolesAndGrantsSurviveReopeningAndMigrationIsNotReapplied() {
		String username = uniqueName();
		HttpUser user;
		MqttIdentity device;
		MqttIdentity service;
		MqttGrant publish;
		MqttGrant subscribe;
		try {
			try (var context = context()) {
				var users = context.getBean(HttpUserRepository.class);
				var mqtt = context.getBean(MqttIdentityRepository.class);
				user = users.create(username, hash, true, Set.of(Role.ADMIN, Role.USER));
				device = mqtt.create(username, hash, true, Kind.DEVICE, username);
				service = mqtt.create(username + "svc", hash, false, Kind.SERVICE, null);
				publish = new MqttGrant(device.id(), "sensors/" + username + "/measur", Permission.PUBLISH);
				subscribe = new MqttGrant(service.id(), publish.topic(), Permission.SUBSCRIBE);
				mqtt.addGrant(publish);
				mqtt.addGrant(subscribe);
				assertTrue(users.findByUsername("absent-" + username).isEmpty());
				assertTrue(mqtt.findByUsername("absent-" + username).isEmpty());
				assertTrue(users.findByUsername(username + " ").isEmpty());
				assertTrue(mqtt.findByUsername(username + " ").isEmpty());
				assertTrue(users.findByUsername(username.toUpperCase(java.util.Locale.ROOT)).isEmpty());
				assertFalse(user.toString().contains(hash.encoded()));
				assertFalse(device.toString().contains(hash.encoded()));
			}
			assertEquals(0, database.flyway().migrate().migrationsExecuted);
			database.flyway().validate();
			try (var context = context()) {
				var users = context.getBean(HttpUserRepository.class);
				var mqtt = context.getBean(MqttIdentityRepository.class);
				assertEquals(user, users.findByUsername(username).orElseThrow());
				assertEquals(device, mqtt.findByUsername(username).orElseThrow());
				assertEquals(service, mqtt.findByUsername(username + "svc").orElseThrow());
				assertEquals(Set.of(publish), Set.copyOf(mqtt.findGrants(device.id())));
				assertEquals(Set.of(subscribe), Set.copyOf(mqtt.findGrants(service.id())));
			}
		} finally {
			// Only remove rows created by this test, including with an external test DB.
			try (var context = context()) {
				JdbcClient jdbc = context.getBean(JdbcClient.class);
				jdbc.sql("""
						DELETE FROM mqtt_grant WHERE identity_id IN
						(SELECT id FROM mqtt_identity WHERE username IN (:name, :service))
						""").param("name", username).param("service", username + "svc").update();
				jdbc.sql("DELETE FROM mqtt_identity WHERE username IN (:name, :service)")
						.param("name", username).param("service", username + "svc").update();
				jdbc.sql("DELETE FROM http_user_role WHERE user_id IN (SELECT id FROM http_user WHERE username = :name)")
						.param("name", username).update();
				jdbc.sql("DELETE FROM http_user WHERE username = :name").param("name", username).update();
			}
		}
	}

	@Test
	void databaseEnforcesUniquenessRelationshipsAndCredentialAndPrincipalConstraints() {
		try (var context = context()) {
			var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			transaction.executeWithoutResult(status -> {
				status.setRollbackOnly();
				var users = context.getBean(HttpUserRepository.class);
				var mqtt = context.getBean(MqttIdentityRepository.class);
				var jdbc = context.getBean(JdbcClient.class);
				String name = uniqueName();
				var user = users.create(name, hash, true, Set.of(Role.SENSOR));
				var device = mqtt.create(name, hash, true, Kind.DEVICE, name);
				assertThrows(DataAccessException.class, () -> users.create(name, hash, false, Set.of()));
				assertThrows(DataAccessException.class, () -> mqtt.create(name, hash, true, Kind.SERVICE, null));
				assertThrows(DataAccessException.class, () -> mqtt.create(name + "2", hash, true, Kind.DEVICE, name));
				assertThrows(DataAccessException.class, () -> mqtt.create(name + "2", hash, true, Kind.DEVICE, null));
				assertThrows(DataAccessException.class, () -> mqtt.create(name + "2", hash, true, Kind.SERVICE, name));
				assertThrows(DataAccessException.class, () -> jdbc.sql(
						"INSERT INTO http_user_role (user_id, role) VALUES (:id, 'SUPERUSER')")
						.param("id", user.id()).update());
				assertThrows(DataAccessException.class, () -> jdbc.sql(
						"INSERT INTO http_user_role (user_id, role) VALUES (-1, 'USER')").update());
				assertThrows(DataAccessException.class, () -> mqtt.addGrant(
						new MqttGrant(-1, "sensors/test/measur", Permission.PUBLISH)));
				assertThrows(DataAccessException.class, () -> mqtt.addGrant(
						new MqttGrant(device.id(), "sensors/+/measur", Permission.PUBLISH)));
				var grant = new MqttGrant(device.id(), "sensors/test/measur", Permission.PUBLISH);
				mqtt.addGrant(grant);
				assertThrows(DataAccessException.class, () -> mqtt.addGrant(grant));
				for (String invalidHash : new String[] {
						"plaintext-is-not-a-hash", "{noop}plaintext-is-not-a-hash", hash.encoded() + "\n"}) {
					assertThrows(DataAccessException.class, () -> jdbc.sql(
							"UPDATE http_user SET password_hash = :hash WHERE id = :id")
							.param("hash", invalidHash).param("id", user.id()).update());
					assertThrows(DataAccessException.class, () -> jdbc.sql(
							"UPDATE mqtt_identity SET password_hash = :hash WHERE id = :id")
							.param("hash", invalidHash).param("id", device.id()).update());
				}
				assertThrows(DataAccessException.class, () -> jdbc.sql(
						"UPDATE http_user SET password_hash = NULL WHERE id = :id").param("id", user.id()).update());
				assertThrows(DataAccessException.class, () -> jdbc.sql(
						"UPDATE mqtt_identity SET device_id = :device WHERE id = :id")
						.param("device", name + "\n").param("id", device.id()).update());
				assertThrows(DataAccessException.class, () -> jdbc.sql(
						"INSERT INTO mqtt_grant (identity_id, topic, permission) VALUES (:id, 'test', 'ALL')")
						.param("id", device.id()).update());
				assertThrows(DataAccessException.class, () -> mqtt.addGrant(
						new MqttGrant(device.id(), "$SYS/test", Permission.SUBSCRIBE)));
			});
		}
	}

	@Test
	void exactTopicsDoNotIgnoreTrailingSpaces() {
		try (var context = context()) {
			new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
				status.setRollbackOnly();
				var mqtt = context.getBean(MqttIdentityRepository.class);
				var device = mqtt.create(uniqueName(), hash, false, Kind.SERVICE, null);
				var topic = new MqttGrant(device.id(), "sensors/test/measur", Permission.SUBSCRIBE);
				var differentTopic = new MqttGrant(device.id(), topic.topic() + " ", Permission.SUBSCRIBE);
				mqtt.addGrant(topic);
				mqtt.addGrant(differentTopic);
				assertEquals(Set.of(topic, differentTopic), Set.copyOf(mqtt.findGrants(device.id())));
				assertEquals(1, context.getBean(JdbcClient.class)
						.sql("SELECT COUNT(*) FROM mqtt_grant WHERE identity_id = :id AND topic = :topic")
						.param("id", device.id()).param("topic", topic.topic()).query(Integer.class).single());
			});
		}
	}

	@Test
	void creatingUserAndRolesIsAtomic() {
		try (var context = context()) {
			var users = context.getBean(HttpUserRepository.class);
			Set<Role> invalidRoles = new LinkedHashSet<>();
			invalidRoles.add(Role.USER);
			invalidRoles.add(null);
			String name = uniqueName();
			assertThrows(NullPointerException.class, () -> users.create(name, hash, true, invalidRoles));
			assertTrue(users.findByUsername(name).isEmpty());
		}
	}

	@Test
	void repositoryConnectionErrorsPropagateInsteadOfLookingLikeMissingAccounts() {
		var unavailable = JdbcClient.create(new DriverManagerDataSource(
				"jdbc:mariadb://127.0.0.1:1/unavailable?connectTimeout=1000", "unavailable", "unavailable"));
		assertThrows(DataAccessException.class, () -> new HttpUserRepository(unavailable).findByUsername("test"));
		assertThrows(DataAccessException.class, () -> new MqttIdentityRepository(unavailable).findByUsername("test"));
	}

	private static String uniqueName() {
		return "it" + UUID.randomUUID().toString().replace("-", "");
	}

	private static AnnotationConfigApplicationContext context() {
		var context = new AnnotationConfigApplicationContext();
		context.registerBean(DataSource.class, database::dataSource);
		context.register(RepositoryConfiguration.class);
		context.refresh();
		return context;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@Import({HttpUserRepository.class, MqttIdentityRepository.class})
	static class RepositoryConfiguration {
		@Bean
		JdbcClient jdbcClient(DataSource dataSource) {
			return JdbcClient.create(dataSource);
		}

		@Bean
		PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}
	}
}
