package nl.tufa.poc.bme680.identity.infrastructure.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.application.UserAdministrationService;
import nl.tufa.poc.bme680.identity.application.UserSummary;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdministrationController {
	private final UserAdministrationService users;

	@GetMapping
	public List<UserSummary> list() {
		return users.list();
	}

	@PostMapping(consumes = "application/json")
	@ResponseStatus(HttpStatus.CREATED)
	public UserSummary create(@RequestBody @Valid UserRequests.Create request) {
		return users.create(request.username(), request.password(), request.enabled(), request.roles());
	}

	@PutMapping(value = "/{id}/roles", consumes = "application/json")
	public UserSummary roles(@PathVariable long id, @RequestBody @Valid UserRequests.Roles request) {
		return users.changeRoles(id, request.roles());
	}

	@PutMapping(value = "/{id}/enabled", consumes = "application/json")
	public UserSummary enabled(@PathVariable long id, @RequestBody @Valid UserRequests.Enabled request) {
		return users.setEnabled(id, request.enabled());
	}

	@PutMapping(value = "/{id}/password", consumes = "application/json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void password(@PathVariable long id, @RequestBody @Valid UserRequests.Password request) {
		users.resetPassword(id, request.password());
	}
}
