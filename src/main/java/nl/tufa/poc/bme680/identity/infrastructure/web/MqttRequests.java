package nl.tufa.poc.bme680.identity.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class MqttRequests {
	private MqttRequests() {
	}

	public record Device(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9_.@-]{1,64}") String username,
			@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String deviceId) {
	}

	public record Service(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.@-]{1,64}") String username) {
	}

	public record Rotate() {
	}

	public record Enabled(@NotNull Boolean enabled) {
	}
}
