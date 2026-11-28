package nl.tufa.poc.bme680.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.broker.subscriptions.Topic;
import nl.tufa.poc.bme680.identity.application.MqttAuthenticationService;
import nl.tufa.poc.bme680.identity.application.MqttAuthorizationService;
import nl.tufa.poc.bme680.identity.application.MqttBrokerUnavailableException;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.application.port.MqttSessionBarrier;

/**
 * A fresh broker instance per generation barrier. Admission is drained and permanently
 * closed for the old instance before Moquette shuts down its channels and session loops.
 * A failed restart leaves admission closed; never fall back to anonymous.
 */
public final class ManagedMqttBroker implements MqttSessionBarrier {
	private static final Logger LOG = LoggerFactory.getLogger(ManagedMqttBroker.class);
	private final Properties properties;
	private final MqttAuthenticationService authentication;
	private final MqttAuthorizationService authorization;
	private Server server;
	private Admission admission = new Admission();

	private static final class Admission {
		final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
		boolean open;
	}

	public ManagedMqttBroker(Properties properties, MqttAuthenticationService authentication,
			MqttAuthorizationService authorization) {
		this.properties = new Properties();
		this.properties.putAll(properties);
		this.authentication = authentication;
		this.authorization = authorization;
	}

	public synchronized void start() throws IOException {
		if (server != null) {
			throw new IllegalStateException("MQTT broker must be stopped before starting");
		}
		Server next = new Server();
		server = next;
		// Authentication is enabled before binding, with dependencies already initialized.
		Admission epoch = new Admission();
		epoch.open = true;
		admission = epoch;
		try {
			next.startServer(new MemoryConfig(properties), List.of(), null,
					(clientId, username, password) -> authenticate(epoch, clientId, username, password),
					new IAuthorizatorPolicy() {
						// Moquette invokes this for normal PUBLISH/SUBSCRIBE, not wills or each delivery.
						@Override
						public boolean canRead(Topic topic, String username, String clientId) {
							return permitted(epoch, clientId, username, topic, Permission.SUBSCRIBE);
						}
						@Override
						public boolean canWrite(Topic topic, String username, String clientId) {
							return permitted(epoch, clientId, username, topic, Permission.PUBLISH);
						}
					});
		} catch (IOException | RuntimeException failure) {
			closeAdmission();
			try {
				next.stopServer();
				server = null;
			} catch (RuntimeException ignored) {
				// A partially initialized broker may not support stopServer.
			}
			throw failure;
		}
	}

	private boolean authenticate(Admission epoch, String clientId, String username, byte[] password) {
		epoch.lock.readLock().lock();
		try {
			if (!epoch.open || password == null || password.length == 0 || password.length > 256) {
				LOG.warn("MQTT authentication denied");
				return false;
			}
			String decoded = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(password)).toString();
			boolean accepted = authentication.authenticate(clientId, username, decoded);
			if (!accepted) {
				LOG.warn("MQTT authentication denied");
			}
			return accepted;
		} catch (CharacterCodingException | RuntimeException failure) {
			// No raw credentials, hashes, or exception messages in diagnostics.
			LOG.warn("MQTT authentication denied: invalid credentials or identity store unavailable");
			return false;
		} finally {
			epoch.lock.readLock().unlock();
		}
	}

	private boolean permitted(Admission epoch, String clientId, String username, Topic topic, Permission permission) {
		epoch.lock.readLock().lock();
		try {
			boolean allowed = epoch.open && authorization.permits(username, clientId, topic.toString(), permission);
			if (!allowed) {
				LOG.warn("MQTT topic operation denied");
			}
			return allowed;
		} catch (RuntimeException failure) {
			LOG.warn("MQTT topic operation denied: identity or grant store unavailable");
			return false;
		} finally {
			epoch.lock.readLock().unlock();
		}
	}

	@Override
	public synchronized <T> T withDisconnectedSessions(Supplier<T> change) {
		stop();
		// On a failed/uncertain transaction leave the listener stopped. Administrative retry
		// or application restart is required; never reopen on ambiguous commit outcome.
		T result;
		try {
			result = change.get();
		} catch (RuntimeException failure) {
			LOG.error("MQTT administration failed; broker listener remains stopped");
			throw failure;
		}
		try {
			start();
		} catch (IOException | RuntimeException failure) {
			LOG.error("MQTT broker restart failed; listener admission remains closed");
			throw new MqttBrokerUnavailableException();
		}
		return result;
	}

	public synchronized void stop() {
		closeAdmission();
		if (server != null) {
			server.stopServer();
			server = null;
		}
	}

	private void closeAdmission() {
		admission.lock.writeLock().lock();
		try {
			admission.open = false;
		} finally {
			admission.lock.writeLock().unlock();
		}
	}
}
