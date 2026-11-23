package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.security.WebSecurity;

class PasswordHashTest {

	@Test
	void sharedEncoderProducesUsablePrefixedHashesAndRedactsDiagnostics() {
		var encoder = new WebSecurity().passwordEncoder();
		var hash = new PasswordHash(encoder.encode("test-only-secret"));
		assertTrue(hash.encoded().startsWith("{bcrypt}"));
		assertTrue(encoder.matches("test-only-secret", hash.encoded()));
		assertFalse(encoder.matches("wrong-test-secret", hash.encoded()));
		assertEquals("[REDACTED]", hash.toString());
	}

	@Test
	void rawPasswordsAndNonHashEncodingsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new PasswordHash("test-only-secret"));
		assertThrows(IllegalArgumentException.class, () -> new PasswordHash("{noop}test-only-secret"));
	}
}
