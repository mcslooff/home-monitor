package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import nl.tufa.poc.bme680.DatabaseFixture;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.HttpUserEntity;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.IdentityConstraintProbeRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.JpaHttpUserRepositoryAdapter;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.JpaMqttIdentityRepositoryAdapter;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.SpringDataHttpUserRepository;
import nl.tufa.poc.bme680.security.WebSecurity;

class IdentityStoreIT {
	private static DatabaseFixture database;
	private static PasswordHash hash;
	private ConfigurableApplicationContext context;
	private HttpUserRepository users;
	private MqttIdentityRepository mqtt;
	private IdentityConstraintProbeRepository probes;
	private String prefix;

	@BeforeAll
	static void startDatabase() {
		database = DatabaseFixture.open();
		hash = new PasswordHash(new WebSecurity().passwordEncoder().encode("test-only-secret"));
	}

	@AfterAll
	static void stopDatabase() {
		if (database != null) {
			database.close();
		}
	}

	@BeforeEach
	void openContext() {
		prefix = "it" + UUID.randomUUID().toString().replace("-", "");
		reopenContext();
	}

	@AfterEach
	void cleanUpOwnedRowsAndCloseContext() {
		if (context != null) {
			try {
				new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
					probes.deleteGrants(prefix + "%");
					probes.deleteMqttIdentities(prefix + "%");
					probes.deleteRoles(prefix + "%");
					probes.deleteUsers(prefix + "%");
				});
			} finally {
				context.close();
			}
		}
	}

	@Test
	void identitiesRolesAndGrantsSurviveReopeningAndMigrationIsNotReapplied() {
		var user = users.save(new HttpUser(null, prefix, hash, true, Set.of(Role.ADMIN, Role.USER)));
		var device = mqtt.save(new MqttIdentity(null, prefix, hash, true, Kind.DEVICE, prefix));
		var service = mqtt.save(new MqttIdentity(null, prefix + "svc", hash, false, Kind.SERVICE, null));
		var publish = new MqttGrant(device.id(), "sensors/" + prefix + "/measur", Permission.PUBLISH);
		var subscribe = new MqttGrant(service.id(), publish.topic(), Permission.SUBSCRIBE);
		mqtt.addGrant(publish);
		mqtt.addGrant(subscribe);
		assertTrue(users.findByUsername("absent-" + prefix).isEmpty());
		assertTrue(mqtt.findByUsername("absent-" + prefix).isEmpty());
		assertTrue(users.findByUsername(prefix + " ").isEmpty());
		assertTrue(mqtt.findByUsername(prefix + " ").isEmpty());
		assertTrue(users.findByUsername(prefix.toUpperCase(java.util.Locale.ROOT)).isEmpty());
		assertFalse(user.toString().contains(hash.encoded()));
		assertFalse(device.toString().contains(hash.encoded()));
		reopenContext();
		assertEquals(0, database.flyway().migrate().migrationsExecuted);
		database.flyway().validate();
		assertEquals(user, users.findByUsername(prefix).orElseThrow());
		assertEquals(device, mqtt.findByUsername(prefix).orElseThrow());
		assertEquals(service, mqtt.findByUsername(prefix + "svc").orElseThrow());
		assertEquals(Set.of(publish), Set.copyOf(mqtt.findGrants(device.id())));
		assertEquals(Set.of(subscribe), Set.copyOf(mqtt.findGrants(service.id())));
	}

	@Test
	void savingExistingModelsUpdatesUserRolesAndIdentityState() {
		var user = users.save(new HttpUser(null, prefix, hash, true, Set.of(Role.USER, Role.SENSOR)));
		var updated = new HttpUser(user.id(), prefix, hash, false, Set.of(Role.ADMIN));
		assertEquals(updated, users.save(updated));
		assertEquals(updated, users.findByUsername(prefix).orElseThrow());
		var service = mqtt.save(new MqttIdentity(null, prefix, hash, true, Kind.SERVICE, null));
		var disabled = new MqttIdentity(service.id(), prefix, hash, false, Kind.SERVICE, null);
		assertEquals(disabled, mqtt.save(disabled));
		assertEquals(disabled, mqtt.findByUsername(prefix).orElseThrow());
	}

	@Test
	void databaseEnforcesUniquenessRelationshipsAndCredentialAndPrincipalConstraints() {
		var user = users.save(new HttpUser(null, prefix, hash, true, Set.of(Role.SENSOR)));
		var device = mqtt.save(new MqttIdentity(null, prefix, hash, true, Kind.DEVICE, prefix));
		assertThrows(DataAccessException.class, () -> users.save(new HttpUser(null, prefix, hash, false, Set.of())));
		assertThrows(DataAccessException.class, () -> mqtt.save(
				new MqttIdentity(null, prefix, hash, true, Kind.SERVICE, null)));
		assertThrows(DataAccessException.class, () -> mqtt.save(
				new MqttIdentity(null, prefix + "2", hash, true, Kind.DEVICE, prefix)));
		assertThrows(DataAccessException.class, () -> mqtt.save(
				new MqttIdentity(null, prefix + "2", hash, true, Kind.DEVICE, null)));
		assertThrows(DataAccessException.class, () -> mqtt.save(
				new MqttIdentity(null, prefix + "2", hash, true, Kind.SERVICE, prefix)));
		assertThrows(DataAccessException.class, () -> probes.insertRole(user.id(), "SUPERUSER"));
		assertThrows(DataAccessException.class, () -> probes.insertRole(-1, "USER"));
		assertThrows(DataAccessException.class, () -> mqtt.addGrant(
				new MqttGrant(-1, "sensors/test/measur", Permission.PUBLISH)));
		assertThrows(DataAccessException.class, () -> mqtt.addGrant(
				new MqttGrant(device.id(), "sensors/+/measur", Permission.PUBLISH)));
		var grant = new MqttGrant(device.id(), "sensors/test/measur", Permission.PUBLISH);
		mqtt.addGrant(grant);
		assertThrows(DataAccessException.class, () -> mqtt.addGrant(grant));
		for (String invalidHash : new String[] {
				"plaintext-is-not-a-hash", "{noop}plaintext-is-not-a-hash", hash.encoded() + "\n"}) {
			assertThrows(DataAccessException.class, () -> probes.setUserHash(user.id(), invalidHash));
			assertThrows(DataAccessException.class, () -> probes.setMqttHash(device.id(), invalidHash));
		}
		assertThrows(DataAccessException.class, () -> probes.setUserHash(user.id(), null));
		assertThrows(DataAccessException.class, () -> probes.setDeviceId(device.id(), prefix + "\n"));
		assertThrows(DataAccessException.class, () -> probes.insertGrant(device.id(), "test", "ALL"));
		assertThrows(DataAccessException.class, () -> mqtt.addGrant(
				new MqttGrant(device.id(), "$SYS/test", Permission.SUBSCRIBE)));
	}

	@Test
	void exactTopicsDoNotIgnoreTrailingSpaces() {
		var service = mqtt.save(new MqttIdentity(null, prefix, hash, false, Kind.SERVICE, null));
		var topic = new MqttGrant(service.id(), "sensors/test/measur", Permission.SUBSCRIBE);
		var differentTopic = new MqttGrant(service.id(), topic.topic() + " ", Permission.SUBSCRIBE);
		mqtt.addGrant(topic);
		mqtt.addGrant(differentTopic);
		assertEquals(Set.of(topic, differentTopic), Set.copyOf(mqtt.findGrants(service.id())));
		assertEquals(1, probes.countExactTopic(service.id(), topic.topic()));
	}

	@Test
	void userAndRolesRollbackTogetherWhenAnApplicationTransactionFails() {
		var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
		assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(status -> {
			var user = users.save(new HttpUser(null, prefix, hash, true, Set.of(Role.USER)));
			probes.insertRole(user.id(), "INVALID");
		}));
		assertTrue(users.findByUsername(prefix).isEmpty());
	}

	@Test
	void repositoryReadsAndWritesTreatInjectionPayloadsAsValues() {
		var user = users.save(new HttpUser(null, prefix, hash, true, Set.of(Role.USER)));
		var service = mqtt.save(new MqttIdentity(null, prefix, hash, true, Kind.SERVICE, null));
		assertTrue(users.findByUsername("' OR 1=1 --").isEmpty());
		assertTrue(mqtt.findByUsername("' OR 1=1 --").isEmpty());
		String topic = "sensors/'; DROP TABLE http_user; --";
		var grant = new MqttGrant(service.id(), topic, Permission.SUBSCRIBE);
		mqtt.addGrant(grant);
		assertEquals(Set.of(grant), Set.copyOf(mqtt.findGrants(service.id())));
		assertEquals(user, users.findByUsername(prefix).orElseThrow());
		assertThrows(DataAccessException.class, () -> users.save(
				new HttpUser(null, "'; DROP TABLE mqtt_identity; --", hash, true, Set.of())));
		assertEquals(service, mqtt.findByUsername(prefix).orElseThrow());
	}

	@Test
	void repositoryConnectionErrorsPropagateInsteadOfLookingLikeMissingAccounts() {
		// Exercise the real JPA adapters with a deterministically unavailable pool.
		// Updating credentials + soft eviction can race with background connection creation.
		database.flyway().validate();
		var dataSource = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
		dataSource.close();
		try {
			assertThrows(CannotCreateTransactionException.class, () -> users.findByUsername(prefix));
			assertThrows(CannotCreateTransactionException.class, () -> mqtt.findByUsername(prefix));
		} finally {
			// Reopen a healthy pool for cleanup and subsequent tests.
			reopenContext();
		}
	}

	private void reopenContext() {
		if (context != null) {
			context.close();
		}
		context = new SpringApplicationBuilder(RepositoryConfiguration.class)
				.web(WebApplicationType.NONE)
				.initializers(applicationContext ->
						TestPropertyValues.of(database.springProperties()).applyTo(applicationContext))
				.run();
		users = context.getBean(HttpUserRepository.class);
		mqtt = context.getBean(MqttIdentityRepository.class);
		probes = context.getBean(IdentityConstraintProbeRepository.class);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = HttpUserEntity.class)
	@EnableJpaRepositories(basePackageClasses = SpringDataHttpUserRepository.class)
	@Import({JpaHttpUserRepositoryAdapter.class, JpaMqttIdentityRepositoryAdapter.class})
	static class RepositoryConfiguration {
	}
}
