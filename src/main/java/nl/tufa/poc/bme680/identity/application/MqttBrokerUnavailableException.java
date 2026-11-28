package nl.tufa.poc.bme680.identity.application;

public class MqttBrokerUnavailableException extends RuntimeException {
	public MqttBrokerUnavailableException() {
		super("MQTT broker is unavailable; credentials may have changed, recover the broker and rotate again");
	}
}
