package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import nl.tufa.poc.bme680.identity.application.MqttAuthorizationService;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

class MqttAuthorizationServiceTest {
	private final MqttIdentityRepository identities = mock(MqttIdentityRepository.class);
	private final MqttAuthorizationService policy = new MqttAuthorizationService(identities);
	private final PasswordHash hash = new PasswordHash("{bcrypt}$2a$10$" + "a".repeat(53));

	@Test
	void devicesRequireOwnExactPublishGrantEvenIfExtraGrantsArePresent() {
		var device = new MqttIdentity(1L, "a", hash, true, MqttIdentity.Kind.DEVICE, "a");
		when(identities.findByUsername("a")).thenReturn(Optional.of(device));
		when(identities.findGrants(1)).thenReturn(List.of(
				new MqttGrant(1, "sensors/a/measur", Permission.PUBLISH),
				new MqttGrant(1, "sensors/b/measur", Permission.PUBLISH),
				new MqttGrant(1, "sensors/a/measur", Permission.SUBSCRIBE)));
		assertTrue(policy.permits("a", "a:v1", "sensors/a/measur", Permission.PUBLISH));
		assertFalse(policy.permits("a", "a:v1", "sensors/b/measur", Permission.PUBLISH));
		assertFalse(policy.permits("a", "a:v1", "sensors/a/measur", Permission.SUBSCRIBE));
		when(identities.findGrants(1)).thenReturn(List.of());
		assertFalse(policy.permits("a", "a:v1", "sensors/a/measur", Permission.PUBLISH));
	}

	@Test
	void serviceHasOnlyExplicitExactOperationsAndNoWildcardOrSystemAccess() {
		var service = new MqttIdentity(2L, "backend", hash, true, MqttIdentity.Kind.SERVICE, null);
		when(identities.findByUsername("backend")).thenReturn(Optional.of(service));
		when(identities.findGrants(2)).thenReturn(List.of(
				new MqttGrant(2, "sensors/a/measur", Permission.SUBSCRIBE),
				new MqttGrant(2, "sensors/b/measur", Permission.PUBLISH)));
		assertTrue(policy.permits("backend", "backend:v1:consumer", "sensors/a/measur", Permission.SUBSCRIBE));
		assertTrue(policy.permits("backend", "backend:v1:producer", "sensors/b/measur", Permission.PUBLISH));
		assertFalse(policy.permits("backend", "backend:v1", "sensors/b/measur", Permission.SUBSCRIBE));
		assertFalse(policy.permits("backend", "backend:v1", "sensors/a/measur", Permission.PUBLISH));
		for (String topic : List.of("#", "+", "sensors/+/measur", "sensors/#", "/sensors/a/measur",
				"$SYS/broker", "$share/group/sensors/a/measur", "sensors/a/measur/extra", "sensors//measur")) {
			assertFalse(policy.permits("backend", "backend:v1", topic, Permission.SUBSCRIBE), topic);
		}
	}

	@Test
	void freshIdentityAndGrantStateAreRequiredForEveryCheck() {
		var service = new MqttIdentity(2L, "backend", hash, true, MqttIdentity.Kind.SERVICE, null, 2);
		when(identities.findByUsername("backend")).thenReturn(Optional.of(service));
		when(identities.findGrants(2)).thenReturn(List.of(new MqttGrant(2, "sensors/a/measur", Permission.SUBSCRIBE)));
		assertFalse(policy.permits("backend", "backend:v1", "sensors/a/measur", Permission.SUBSCRIBE));
		assertFalse(policy.permits("other", "backend:v2", "sensors/a/measur", Permission.SUBSCRIBE));
		assertTrue(policy.permits("backend", "backend:v2", "sensors/a/measur", Permission.SUBSCRIBE));
		when(identities.findGrants(2)).thenReturn(List.of());
		assertFalse(policy.permits("backend", "backend:v2", "sensors/a/measur", Permission.SUBSCRIBE));
		when(identities.findByUsername("backend")).thenReturn(Optional.of(
				new MqttIdentity(2L, "backend", hash, false, MqttIdentity.Kind.SERVICE, null, 2)));
		assertFalse(policy.permits("backend", "backend:v2", "sensors/a/measur", Permission.SUBSCRIBE));
	}

	@Test
	void lookupErrorsPropagateForTheBrokerAdapterToDeny() {
		when(identities.findByUsername("a")).thenThrow(new DataAccessResourceFailureException("Store unavailable"));
		assertThrows(DataAccessResourceFailureException.class,
				() -> policy.permits("a", "a:v1", "sensors/a/measur", Permission.PUBLISH));
		assertFalse(policy.permits(null, "a:v1", null, Permission.PUBLISH));
	}
}
