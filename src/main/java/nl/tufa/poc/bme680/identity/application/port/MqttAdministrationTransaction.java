package nl.tufa.poc.bme680.identity.application.port;

import java.util.function.Supplier;

/** An independent, exclusive transaction for one complete MQTT administration use case. */
public interface MqttAdministrationTransaction {
	<T> T execute(Supplier<T> work);
}
