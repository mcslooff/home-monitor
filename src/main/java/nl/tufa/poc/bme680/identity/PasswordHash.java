package nl.tufa.poc.bme680.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An encoded credential, never a raw password. New algorithms require an explicit
 * policy/schema change; algorithm identifiers allow existing hashes to coexist.
 */
public record PasswordHash(String encoded) {

	private static final Pattern BCRYPT = Pattern.compile(
			"\\{bcrypt}\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");

	public PasswordHash {
		Objects.requireNonNull(encoded, "Password hash is required");
		if (!BCRYPT.matcher(encoded).matches()) {
			throw new IllegalArgumentException("Expected an algorithm-prefixed BCrypt hash");
		}
	}

	@Override
	public String toString() {
		return "[REDACTED]";
	}
}
