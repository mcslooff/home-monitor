package nl.tufa.poc.bme680.config;

import java.io.IOException;
import java.util.Properties;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddedMqttConfiguration {

    @Bean(name = "mqttBroker", destroyMethod = "stopServer")
    public Server mqttBroker(
            @Value("${mqtt.broker.host:127.0.0.1}") String host,
            @Value("${mqtt.broker.port:1883}") int port)
            throws IOException {

        Properties properties = new Properties();
        properties.setProperty("host", host);
        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("allow_anonymous", "true");

        // In-memory storage for this demo.
        properties.setProperty("persistence_enabled", "false");

        Server broker = new Server();
        broker.startServer(new MemoryConfig(properties));

        return broker;
    }
}