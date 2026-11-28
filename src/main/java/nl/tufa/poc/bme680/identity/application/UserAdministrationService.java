package nl.tufa.poc.bme680.identity.application;

import static nl.tufa.poc.bme680.identity.application.UserManagementException.Reason.CONFLICT;
import static nl.tufa.poc.bme680.identity.application.UserManagementException.Reason.INVALID_REQUEST;
import static nl.tufa.poc.bme680.identity.application.UserManagementException.Reason.NOT_FOUND;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.application.port.UserAdministrationTransaction;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;

public class UserAdministrationService {
	private final HttpUserRepository users;
	private final PasswordHasher passwords;
	private final UserAdministrationTransaction transaction;

	public UserAdministrationService(HttpUserRepository users, PasswordHasher passwords,
			UserAdministrationTransaction transaction) {
		this.users = users;
		this.passwords = passwords;
		this.transaction = transaction;
	}

	public void bootstrap(String username, String password) {
		transaction.execute(() -> {
			if (users.count() != 0) {
				if (users.countEnabledAdministrators() == 0) {
					throw new UserManagementException(CONFLICT,
							"No enabled administrator exists; restricted operator recovery is required");
				}
				return null;
			}
			if (username == null || username.isBlank() || password == null || password.isBlank()) {
				throw new UserManagementException(INVALID_REQUEST,
						"Bootstrap administrator credentials are required for an empty user store");
			}
			validateUsername(username);
			validatePassword(password);
			users.save(new HttpUser(null, username, passwords.hash(password), true, Set.of(Role.ADMIN)));
			return null;
		});
	}

	public UserSummary create(String username, String password, boolean enabled, Set<Role> roles) {
		validateUsername(username);
		validatePassword(password);
		validateRoles(roles);
		return transaction.execute(() -> {
			if (users.findByUsername(username).isPresent()) {
				throw new UserManagementException(CONFLICT, "Username already exists");
			}
			return UserSummary.from(users.save(new HttpUser(null, username, passwords.hash(password), enabled, roles)));
		});
	}

	public List<UserSummary> list() {
		return users.findAll().stream().map(UserSummary::from).toList();
	}

	public UserSummary changeRoles(long id, Set<Role> roles) {
		validateRoles(roles);
		return transaction.execute(() -> {
			HttpUser user = requireUser(id);
			protectLastAdministrator(user, user.enabled(), roles);
			return UserSummary.from(users.save(
					new HttpUser(user.id(), user.username(), user.passwordHash(), user.enabled(), roles)));
		});
	}

	public UserSummary setEnabled(long id, boolean enabled) {
		return transaction.execute(() -> {
			HttpUser user = requireUser(id);
			protectLastAdministrator(user, enabled, user.roles());
			return UserSummary.from(users.save(
					new HttpUser(user.id(), user.username(), user.passwordHash(), enabled, user.roles())));
		});
	}

	public void resetPassword(long id, String password) {
		validatePassword(password);
		transaction.execute(() -> {
			HttpUser user = requireUser(id);
			users.save(new HttpUser(user.id(), user.username(), passwords.hash(password), user.enabled(), user.roles()));
			return null;
		});
	}

	private HttpUser requireUser(long id) {
		return users.findById(id).orElseThrow(() -> new UserManagementException(NOT_FOUND, "User not found"));
	}

	private void protectLastAdministrator(HttpUser user, boolean enabled, Set<Role> roles) {
		if (user.enabled() && user.roles().contains(Role.ADMIN) && (!enabled || !roles.contains(Role.ADMIN))
				&& users.countEnabledAdministrators() <= 1) {
			throw new UserManagementException(CONFLICT, "Cannot disable or demote the last enabled administrator");
		}
	}

	private static void validateUsername(String username) {
		if (username == null || !username.matches("[A-Za-z0-9_.@-]{1,64}")) {
			throw new UserManagementException(INVALID_REQUEST, "Invalid username");
		}
	}

	private static void validateRoles(Set<Role> roles) {
		if (roles == null || roles.isEmpty() || roles.stream().anyMatch(java.util.Objects::isNull)) {
			throw new UserManagementException(INVALID_REQUEST, "At least one valid role is required");
		}
	}

	private static void validatePassword(String password) {
		if (password == null || password.isBlank() || password.length() < 12
				|| password.getBytes(StandardCharsets.UTF_8).length > 72 || password.indexOf('\0') >= 0) {
			throw new UserManagementException(INVALID_REQUEST,
					"Password must contain at least 12 characters and at most 72 UTF-8 bytes");
		}
	}
}
