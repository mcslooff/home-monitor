package nl.tufa.poc.bme680.identity.application;

public class UserManagementException extends RuntimeException {
	private final Reason reason;

	public UserManagementException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		INVALID_REQUEST, NOT_FOUND, CONFLICT
	}
}
