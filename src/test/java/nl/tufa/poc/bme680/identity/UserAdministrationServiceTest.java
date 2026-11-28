package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nl.tufa.poc.bme680.identity.application.UserAdministrationService;
import nl.tufa.poc.bme680.identity.application.UserManagementException;
import nl.tufa.poc.bme680.identity.application.port.UserAdministrationTransaction;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;
import nl.tufa.poc.bme680.identity.infrastructure.web.UserRequests;
import nl.tufa.poc.bme680.security.WebSecurity;

class UserAdministrationServiceTest {
	private final HttpUserRepository repository = mock(HttpUserRepository.class);

	private UserAdministrationService service() {
		var passwordEncoder = new WebSecurity().passwordEncoder();
		return new UserAdministrationService(repository, raw -> new PasswordHash(passwordEncoder.encode(raw)),
				new UserAdministrationTransaction() {
					@Override
					public <T> T execute(Supplier<T> work) {
						return work.get();
					}
				});
	}

	@Test
	void bootstrapRequiresExplicitCredentialsAndNeverResetsAnInitializedStore() {
		var service = service();
		assertThrows(UserManagementException.class, () -> service.bootstrap("", ""));
		assertThrows(UserManagementException.class, () -> service.bootstrap("operator", ""));
		verify(repository, never()).save(any());
		service.bootstrap("operator", "test-bootstrap-secret");
		var captor = ArgumentCaptor.forClass(HttpUser.class);
		verify(repository).save(captor.capture());
		HttpUser admin = captor.getValue();
		assertEquals(Set.of(Role.ADMIN), admin.roles());
		assertTrue(admin.enabled());
		assertTrue(new WebSecurity().passwordEncoder().matches("test-bootstrap-secret", admin.passwordHash().encoded()));
		when(repository.count()).thenReturn(1L);
		when(repository.countEnabledAdministrators()).thenReturn(1L);
		service.bootstrap("another-operator", "other-bootstrap-secret");
		service.bootstrap("", "");
		verify(repository, times(1)).save(any());
		when(repository.countEnabledAdministrators()).thenReturn(0L);
		assertThrows(UserManagementException.class, () -> service.bootstrap("new-admin", "test-bootstrap-secret"));
	}

	@Test
	void validatesBcryptByteLimitAndRedactsCredentialDtos() {
		String tooManyUtf8Bytes = "é".repeat(40);
		assertThrows(UserManagementException.class, () ->
				service().create("user", tooManyUtf8Bytes, true, Set.of(Role.USER)));
		assertThrows(UserManagementException.class, () ->
				service().create("user", "test-valid-secret", true, Set.of()));
		var request = new UserRequests.Create("user", "test-valid-secret", true, Set.of(Role.USER));
		assertFalse(request.toString().contains("test-valid-secret"));
		assertFalse(new UserRequests.Password("test-valid-secret").toString().contains("test-valid-secret"));
		verify(repository, never()).save(any());
	}
}
