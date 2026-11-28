package nl.tufa.poc.bme680.identity.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Public identifiers bind a connection to a principal and credential generation, not to a secret. */
public record MqttClientId(String username, long generation, String connection) {
	private static final Pattern FORMAT = Pattern.compile(
			"([A-Za-z0-9_.@-]{1,64}):v([1-9][0-9]{0,18})(?::([A-Za-z0-9_-]{1,32}))?");

	public static Optional<MqttClientId> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		var match = FORMAT.matcher(value);
		if (!match.matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new MqttClientId(match.group(1), Long.parseLong(match.group(2)), match.group(3)));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public boolean belongsTo(MqttIdentity identity) {
		return username.equals(identity.username()) && generation == identity.credentialGeneration()
				&& (identity.kind() == MqttIdentity.Kind.SERVICE || connection == null);
	}

	public static String base(MqttIdentity identity) {
		return identity.username() + ":v" + identity.credentialGeneration();
	}

	public static List<String> recommended(MqttIdentity identity) {
		String base = base(identity);
		return identity.kind() == MqttIdentity.Kind.DEVICE
				? List.of(base) : List.of(base + ":consumer", base + ":producer");
	}
}
