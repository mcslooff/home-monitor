package nl.tufa.poc.bme680.identity.application.port;

import java.util.function.Supplier;

/** Disconnect all broker sessions before changing credentials and reopen only after the change commits. */
public interface MqttSessionBarrier {
	<T> T withDisconnectedSessions(Supplier<T> change);
}
