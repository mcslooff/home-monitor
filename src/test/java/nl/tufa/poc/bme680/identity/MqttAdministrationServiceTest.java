package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nl.tufa.poc.bme680.identity.application.IssuedMqttCredential;
import nl.tufa.poc.bme680.identity.application.MqttAdministrationService;
import nl.tufa.poc.bme680.identity.application.MqttManagementException;
import nl.tufa.poc.bme680.identity.application.port.MqttAdministrationTransaction;
import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;
import nl.tufa.poc.bme680.identity.infrastructure.SecureMqttSecretGenerator;
import nl.tufa.poc.bme680.security.WebSecurity;

class MqttAdministrationServiceTest {
	private final MqttIdentityRepository identities = mock(MqttIdentityRepository.class);
	private final PasswordHasher passwords = mock(PasswordHasher.class);
	private static final String SECRET = "A".repeat(43);
	private PasswordHash hash;

	@BeforeEach
	void configure() {
		hash = new PasswordHash(new WebSecurity().passwordEncoder().encode("test-only-secret"));
		when(passwords.hash(SECRET)).thenReturn(hash);
		when(identities.save(any())).thenAnswer(call -> {
			MqttIdentity identity = call.getArgument(0);
			return new MqttIdentity(7L, identity.username(), identity.passwordHash(),
					identity.enabled(), identity.kind(), identity.deviceId());
		});
	}

	@Test
	void secretGeneratorUses256BitsAndIssuedCredentialDiagnosticsAreRedacted() {
		var generator = new SecureMqttSecretGenerator();
		String first = generator.generate();
		assertEquals(43, first.length());
		assertEquals(32, Base64.getUrlDecoder().decode(first).length);
		assertNotEquals(first, generator.generate());
		var issued = new IssuedMqttCredential(null, first);
		assertFalse(issued.toString().contains(first));
	}

	@Test
	void rejectsTopicEscapesBeforeAccessingStorage() {
		for (String invalid : List.of("room/other", "+", "#", "$SYS", "..", "x".repeat(65))) {
			assertThrows(MqttManagementException.class, () -> service(Optional.empty()).createDevice("sensor", invalid));
		}
		verifyNoInteractions(identities);
	}

	@Test
	void producerOptInGrantsExactlyOnePublishTopicAndRequiresAProvisionedDevice() {
		var device = new MqttIdentity(1L, "sensor", hash, true, Kind.DEVICE, "room");
		when(identities.findByDeviceId("room")).thenReturn(Optional.of(device));
		when(identities.findAll()).thenReturn(List.of(device));
		service(Optional.of("sensors/room/measur")).createService("backend");
		var grants = ArgumentCaptor.forClass(MqttGrant.class);
		verify(identities, org.mockito.Mockito.times(2)).addGrant(grants.capture());
		assertEquals(List.of(
				new MqttGrant(7, "sensors/room/measur", Permission.SUBSCRIBE),
				new MqttGrant(7, "sensors/room/measur", Permission.PUBLISH)), grants.getAllValues());
	}

	@Test
	void misconfiguredOrUnprovisionedPublisherTopicCannotCreateAnAccount() {
		assertThrows(MqttManagementException.class, () ->
				service(Optional.of("sensors/+/measur")).createService("backend"));
		assertThrows(MqttManagementException.class, () ->
				service(Optional.of("sensors/missing/measur")).createService("backend"));
		verify(identities, never()).save(any());
	}

	private MqttAdministrationService service(Optional<String> producerTopic) {
		return new MqttAdministrationService(identities, passwords, () -> SECRET,
				new MqttAdministrationTransaction() {
					@Override
					public <T> T execute(Supplier<T> work) {
						return work.get();
					}
				}, producerTopic, new nl.tufa.poc.bme680.identity.application.port.MqttSessionBarrier() {
					public <T> T withDisconnectedSessions(Supplier<T> change) {
						return change.get();
					}
				});
	}
}
