package nl.tufa.poc.bme680;

import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.testcontainers.mariadb.MariaDBContainer;

import nl.tufa.poc.bme680.identity.infrastructure.persistence.HttpUserEntity;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.SpringDataHttpUserRepository;

/**
 * Uses a disposable container by default. An explicitly supplied, dedicated test
 * database allows verification on machines without a Docker-compatible socket.
 */
public final class DatabaseFixture implements AutoCloseable {

	private final MariaDBContainer container;
	private final String url;
	private final String username;
	private final String password;

	private DatabaseFixture(MariaDBContainer container, String url, String username, String password) {
		this.container = container;
		this.url = url;
		this.username = username;
		this.password = password;
	}

	public static DatabaseFixture open() {
		String externalUrl = System.getenv("TEST_DATABASE_URL");
		if (externalUrl != null && !externalUrl.isBlank()) {
			return new DatabaseFixture(null, externalUrl,
					requiredEnvironment("TEST_DATABASE_USERNAME"), requiredEnvironment("TEST_DATABASE_PASSWORD"));
		}
		MariaDBContainer container = new MariaDBContainer("mariadb:11.8");
		try {
			container.start();
			return new DatabaseFixture(container, container.getJdbcUrl(),
					container.getUsername(), container.getPassword());
		} catch (RuntimeException e) {
			container.close();
			throw e;
		}
	}

	public DataSource dataSource() {
		return new DriverManagerDataSource(url, username, password);
	}

	public Flyway flyway() {
		return Flyway.configure().dataSource(dataSource()).cleanDisabled(true).load();
	}

	public Map<String, String> springProperties() {
		return Map.of("spring.datasource.url", url,
				"spring.datasource.username", username, "spring.datasource.password", password);
	}

	public void configure(ProcessBuilder builder) {
		builder.environment().put("DB_URL", url);
		builder.environment().put("DB_USERNAME", username);
		builder.environment().put("DB_PASSWORD", password);
	}

	public void removeHttpTestUser(String username, String password) {
		if (container != null) {
			return; // The disposable database will be removed in close().
		}
		// The packaged process cannot share a transaction with its parent test.
		// Remove only its generated account, after verifying ownership by password.
		try (var context = new SpringApplicationBuilder(CleanupConfiguration.class).web(WebApplicationType.NONE)
				.initializers(application -> TestPropertyValues.of(springProperties()).applyTo(application)).run()) {
			var users = context.getBean(SpringDataHttpUserRepository.class);
			users.findByUsername(username).ifPresent(user -> {
				if (PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(password, user.getPasswordHash())) {
					users.deleteById(user.getId());
				}
			});
		}
	}

	public void removeMqttTestIdentities(String prefix) {
		if (container != null) {
			return;
		}
		try (var context = new SpringApplicationBuilder(CleanupConfiguration.class).web(WebApplicationType.NONE)
				.initializers(application -> TestPropertyValues.of(springProperties()).applyTo(application)).run()) {
			var probes = context.getBean(nl.tufa.poc.bme680.identity.infrastructure.persistence.IdentityConstraintProbeRepository.class);
			probes.deleteGrants(prefix + "%");
			probes.deleteMqttIdentities(prefix + "%");
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = HttpUserEntity.class)
	@EnableJpaRepositories(basePackageClasses = SpringDataHttpUserRepository.class)
	static class CleanupConfiguration {
	}

	private static String requiredEnvironment(String name) {
		String value = System.getenv(name);
		if (value == null) {
			throw new IllegalStateException(name + " must be supplied with TEST_DATABASE_URL");
		}
		return value;
	}

	@Override
	public void close() {
		if (container != null) {
			container.close();
		}
	}
}
