package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import nl.tufa.poc.bme680.identity.application.MqttAuthenticationService;
import nl.tufa.poc.bme680.identity.application.port.PasswordVerifier;
import nl.tufa.poc.bme680.identity.domain.model.MqttClientId;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

class MqttAuthenticationServiceTest {
	private final MqttIdentityRepository identities = mock(MqttIdentityRepository.class);
	private final PasswordVerifier passwords = mock(PasswordVerifier.class);
	private final MqttAuthenticationService authentication = new MqttAuthenticationService(identities, passwords);
	private final PasswordHash hash = new PasswordHash("{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoO5oTuvxewMmHw5KQ8tCkLwZzYfXh5x9e");

	@Test
	void clientIdIsNotProofOfIdentityAndPasswordVerificationIsMandatory() {
		var device = new MqttIdentity(1L, "sensor", hash, true, MqttIdentity.Kind.DEVICE, "device", 2);
		when(identities.findByUsername("sensor")).thenReturn(Optional.of(device));
		when(passwords.matches("correct", hash)).thenReturn(true);
		assertTrue(authentication.authenticate("sensor:v2", "sensor", "correct"));
		assertFalse(authentication.authenticate("sensor:v2", "sensor", "incorrect"));
		assertFalse(authentication.authenticate("sensor:v1", "sensor", "correct"));
		assertFalse(authentication.authenticate("sensor:v2:consumer", "sensor", "correct"));
		assertFalse(authentication.authenticate("other:v2", "sensor", "correct"));
		assertFalse(authentication.authenticate("sensor:v2", null, "correct"));
		assertFalse(authentication.authenticate("sensor:v2", "sensor", null));
	}

	@Test
	void onlyEnabledCurrentServicePrincipalsCanUseDistinctConnectionIds() {
		var service = new MqttIdentity(2L, "backend", hash, true, MqttIdentity.Kind.SERVICE, null, 3);
		when(identities.findByUsername("backend")).thenReturn(Optional.of(service));
		assertTrue(authentication.isCurrentSession("backend:v3:consumer", "backend"));
		assertTrue(authentication.isCurrentSession("backend:v3:producer", "backend"));
		assertFalse(authentication.isCurrentSession("backend:v2:consumer", "backend"));
		when(identities.findByUsername("backend")).thenReturn(Optional.of(
				new MqttIdentity(2L, "backend", hash, false, MqttIdentity.Kind.SERVICE, null, 3)));
		assertFalse(authentication.isCurrentSession("backend:v3:consumer", "backend"));
	}

	@Test
	void malformedOrAmbiguousIdsCannotResolveAnIdentity() {
		for (String id : new String[] {"", "sensor", "sensor:v0", "sensor:v01", "sensor:v-1",
				"sensor:v9223372036854775808", "sensor:v2:", "sensor:v2:../other", "sensor:v2\n", "sénsor:v1"}) {
			assertTrue(MqttClientId.parse(id).isEmpty(), id);
			assertFalse(authentication.authenticate(id, "sensor", "secret"));
		}
		assertTrue(MqttClientId.parse(null).isEmpty());
		verifyNoInteractions(identities, passwords);
	}
}
