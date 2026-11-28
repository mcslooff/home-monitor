package nl.tufa.poc.bme680.config;

import java.io.IOException;
import java.util.Properties;

import nl.tufa.poc.bme680.identity.application.MqttAuthenticationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class EmbeddedMqttConfiguration {

    @Bean(name = "mqttBroker", destroyMethod = "stop")
    @DependsOnDatabaseInitialization
    @DependsOn("httpUserBootstrap")
    public ManagedMqttBroker mqttBroker(
            MqttAuthenticationService authentication,
            @Value("${mqtt.broker.host:127.0.0.1}") String host,
            @Value("${mqtt.broker.port:1883}") int port)
            throws IOException {

        Properties properties = new Properties();
        properties.setProperty("host", host);
        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("allow_anonymous", "false");
        properties.setProperty("allow_zero_byte_client_id", "false");
        properties.setProperty("telemetry_enabled", "false");

        // In-memory storage for this demo.
        properties.setProperty("persistence_enabled", "false");

        ManagedMqttBroker broker = new ManagedMqttBroker(properties, authentication);
        broker.start();

        return broker;
    }
}
