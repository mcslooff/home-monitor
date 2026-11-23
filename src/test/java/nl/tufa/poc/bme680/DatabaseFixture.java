package nl.tufa.poc.bme680;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mariadb.MariaDBContainer;

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

	public void configure(ProcessBuilder builder) {
		builder.environment().put("DB_URL", url);
		builder.environment().put("DB_USERNAME", username);
		builder.environment().put("DB_PASSWORD", password);
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
