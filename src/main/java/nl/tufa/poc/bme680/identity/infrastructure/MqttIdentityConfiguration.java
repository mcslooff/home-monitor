package nl.tufa.poc.bme680.identity.infrastructure;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nl.tufa.poc.bme680.identity.application.MqttAdministrationService;
import nl.tufa.poc.bme680.identity.application.MqttAuthenticationService;
import nl.tufa.poc.bme680.identity.application.port.MqttAdministrationTransaction;
import nl.tufa.poc.bme680.identity.application.port.MqttSecretGenerator;
import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.application.port.PasswordVerifier;
import nl.tufa.poc.bme680.identity.application.port.MqttSessionBarrier;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

@Configuration(proxyBeanMethods = false)
public class MqttIdentityConfiguration {
	@Bean
	MqttAuthenticationService mqttAuthenticationService(MqttIdentityRepository identities, PasswordVerifier passwords) {
		return new MqttAuthenticationService(identities, passwords);
	}

	@Bean
	MqttAdministrationService mqttAdministrationService(MqttIdentityRepository identities, PasswordHasher passwords,
			MqttSecretGenerator secrets, MqttAdministrationTransaction transaction, MqttSessionBarrier sessions,
			@Value("${mqtt.producer.enabled:false}") boolean producerEnabled,
			@Value("${mqtt.producer.topic}") String producerTopic) {
		return new MqttAdministrationService(identities, passwords, secrets, transaction,
				producerEnabled ? Optional.of(producerTopic) : Optional.empty(), sessions);
	}
}
