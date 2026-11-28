package nl.tufa.poc.bme680.identity.infrastructure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import nl.tufa.poc.bme680.identity.application.UserAdministrationService;
import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.application.port.UserAdministrationTransaction;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;

@Configuration(proxyBeanMethods = false)
public class UserIdentityConfiguration {
	@Bean
	PasswordHasher identityPasswordHasher(PasswordEncoder encoder) {
		return raw -> new PasswordHash(encoder.encode(raw));
	}

	@Bean
	UserAdministrationService userAdministrationService(HttpUserRepository users, PasswordHasher passwords,
			UserAdministrationTransaction transaction) {
		return new UserAdministrationService(users, passwords, transaction);
	}

	@Bean(name = "httpUserBootstrap")
	@DependsOnDatabaseInitialization
	InitializingBean httpUserBootstrap(UserAdministrationService users,
			@Value("${BOOTSTRAP_ADMIN_USERNAME:}") String username,
			@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
		return () -> users.bootstrap(username, password);
	}
}
