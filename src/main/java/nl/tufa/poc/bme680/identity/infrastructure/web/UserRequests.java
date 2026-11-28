package nl.tufa.poc.bme680.identity.infrastructure.web;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;

public final class UserRequests {
	private UserRequests() {
	}

	public record Create(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9_.@-]{1,64}") String username,
			@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
			@NotBlank @Size(min = 12, max = 72) String password,
			@NotNull Boolean enabled,
			@NotEmpty Set<@NotNull Role> roles) {
		@Override
		public String toString() {
			return "Create[credentials=REDACTED]";
		}
	}

	public record Roles(@NotEmpty Set<@NotNull Role> roles) {
	}

	public record Password(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
			@NotBlank @Size(min = 12, max = 72) String password) {
		@Override
		public String toString() {
			return "Password[REDACTED]";
		}
	}

	public record Enabled(@NotNull Boolean enabled) {
	}
}
