package nl.tufa.poc.bme680.identity.infrastructure.web;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import nl.tufa.poc.bme680.identity.application.UserManagementException;

@RestControllerAdvice(assignableTypes = UserAdministrationController.class)
public class UserAdministrationErrors {
	public record Error(String message) {
	}

	@ExceptionHandler(UserManagementException.class)
	ResponseEntity<Error> business(UserManagementException exception) {
		HttpStatus status = switch (exception.reason()) {
			case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
		};
		return ResponseEntity.status(status).body(new Error(exception.getMessage()));
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	ResponseEntity<Error> invalidRequest() {
		// Never include rejected DTO values (particularly passwords) in responses.
		return ResponseEntity.badRequest().body(new Error("Invalid user request"));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Error> conflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error("User data conflicts with existing records"));
	}

	@ExceptionHandler({DataAccessException.class, TransactionException.class})
	ResponseEntity<Error> unavailable() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Error("Identity store is unavailable"));
	}
}
