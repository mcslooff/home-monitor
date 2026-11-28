package nl.tufa.poc.bme680.identity.application;

import static nl.tufa.poc.bme680.identity.application.MqttManagementException.Reason.CONFLICT;
import static nl.tufa.poc.bme680.identity.application.MqttManagementException.Reason.INVALID_REQUEST;
import static nl.tufa.poc.bme680.identity.application.MqttManagementException.Reason.NOT_FOUND;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import nl.tufa.poc.bme680.identity.application.port.MqttAdministrationTransaction;
import nl.tufa.poc.bme680.identity.application.port.MqttSecretGenerator;
import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;

public class MqttAdministrationService {
	private static final Pattern PRODUCER_TOPIC = Pattern.compile("sensors/([A-Za-z0-9_-]{1,64})/measur");
	private final MqttIdentityRepository identities;
	private final PasswordHasher passwords;
	private final MqttSecretGenerator secrets;
	private final MqttAdministrationTransaction transaction;
	private final Optional<String> producerTopic;

	public MqttAdministrationService(MqttIdentityRepository identities, PasswordHasher passwords,
			MqttSecretGenerator secrets, MqttAdministrationTransaction transaction, Optional<String> producerTopic) {
		this.identities = identities;
		this.passwords = passwords;
		this.secrets = secrets;
		this.transaction = transaction;
		this.producerTopic = producerTopic;
	}

	public IssuedMqttCredential createDevice(String username, String deviceId) {
		validateUsername(username);
		if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,64}")) {
			throw new MqttManagementException(INVALID_REQUEST, "Invalid device ID");
		}
		return transaction.execute(() -> {
			requireUniqueUsername(username);
			if (identities.findByDeviceId(deviceId).isPresent()) {
				throw new MqttManagementException(CONFLICT, "Device ID already exists");
			}
			String secret = newSecret();
			var device = identities.save(new MqttIdentity(null, username, passwords.hash(secret), true, Kind.DEVICE, deviceId));
			String topic = measurementTopic(deviceId);
			identities.addGrant(new MqttGrant(device.id(), topic, Permission.PUBLISH));
			// Account creation order must not change the service's exact subscriptions.
			for (var service : identities.findAll()) {
				if (service.kind() == Kind.SERVICE) {
					identities.addGrant(new MqttGrant(service.id(), topic, Permission.SUBSCRIBE));
				}
			}
			return new IssuedMqttCredential(summary(device), secret);
		});
	}

	public IssuedMqttCredential createService(String username) {
		validateUsername(username);
		return transaction.execute(() -> {
			requireUniqueUsername(username);
			producerTopic.ifPresent(topic -> {
				var match = PRODUCER_TOPIC.matcher(topic);
				if (!match.matches() || identities.findByDeviceId(match.group(1)).isEmpty()) {
					throw new MqttManagementException(CONFLICT,
							"Producer topic must identify a provisioned device measurement topic");
				}
			});
			String secret = newSecret();
			var service = identities.save(new MqttIdentity(null, username, passwords.hash(secret), true, Kind.SERVICE, null));
			for (var device : identities.findAll()) {
				if (device.kind() == Kind.DEVICE) {
					identities.addGrant(new MqttGrant(service.id(), measurementTopic(device.deviceId()), Permission.SUBSCRIBE));
				}
			}
			producerTopic.ifPresent(topic -> identities.addGrant(new MqttGrant(service.id(), topic, Permission.PUBLISH)));
			return new IssuedMqttCredential(summary(service), secret);
		});
	}

	public List<MqttIdentitySummary> list() {
		return identities.findAll().stream().map(this::summary).toList();
	}

	public IssuedMqttCredential rotate(long id) {
		return transaction.execute(() -> {
			var identity = requireIdentity(id);
			String secret = newSecret();
			var updated = identities.save(new MqttIdentity(identity.id(), identity.username(), passwords.hash(secret),
					identity.enabled(), identity.kind(), identity.deviceId()));
			// Broker session termination is integrated in roadmap ticket #5.
			return new IssuedMqttCredential(summary(updated), secret);
		});
	}

	public MqttIdentitySummary setEnabled(long id, boolean enabled) {
		return transaction.execute(() -> {
			var identity = requireIdentity(id);
			var updated = identities.save(new MqttIdentity(identity.id(), identity.username(), identity.passwordHash(),
					enabled, identity.kind(), identity.deviceId()));
			// Persisted state only until broker authentication/revocation in ticket #5.
			return summary(updated);
		});
	}

	private MqttIdentity requireIdentity(long id) {
		return identities.findById(id).orElseThrow(() -> new MqttManagementException(NOT_FOUND, "MQTT identity not found"));
	}

	private MqttIdentitySummary summary(MqttIdentity identity) {
		return MqttIdentitySummary.from(identity, identities.findGrants(identity.id()));
	}

	private void requireUniqueUsername(String username) {
		if (identities.findByUsername(username).isPresent()) {
			throw new MqttManagementException(CONFLICT, "MQTT username already exists");
		}
	}

	private String newSecret() {
		String secret = secrets.generate();
		if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) {
			throw new IllegalStateException("MQTT secret generator returned an invalid credential");
		}
		return secret;
	}

	private static String measurementTopic(String deviceId) {
		return "sensors/" + deviceId + "/measur";
	}

	private static void validateUsername(String username) {
		if (username == null || !username.matches("[A-Za-z0-9_.@-]{1,64}")) {
			throw new MqttManagementException(INVALID_REQUEST, "Invalid MQTT username");
		}
	}
}
