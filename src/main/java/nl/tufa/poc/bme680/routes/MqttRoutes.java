package nl.tufa.poc.bme680.routes;

import java.nio.charset.StandardCharsets;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.tufa.poc.bme680.controller.dto.Bme680MeasurementDto;
import nl.tufa.poc.bme680.services.Bme680Service;

@Slf4j
@Component
@DependsOn({ "mqttBroker" })
@RequiredArgsConstructor
public class MqttRoutes extends RouteBuilder {
	
	private final Bme680Service bme680Service;
	
    @Override
    public void configure() {

        // Consumer: receive MQTT payloads and log them.
        from("paho:{{mqtt.topic}}"
                + "?clientId={{mqtt.client-id}}-consumer"
                + "&qos=1"
                + "&cleanSession=true")
            .routeId("mqtt-consumer")
            .startupOrder(1)
            .unmarshal()
            	.json(JsonLibrary.Jackson, Bme680MeasurementDto.class)  
            .process(exchange -> {
            	Bme680MeasurementDto measurement =
                        exchange.getMessage()
                            .getBody(Bme680MeasurementDto.class);
            	bme680Service.registerMeasurement(measurement);
            })
            .log(LoggingLevel.DEBUG, "Processed topic=${header.CamelMqttTopic}, body=${body}");

        // Producer: reusable route for publishing a JSON string.
        from("direct:publishMeasurement")
            .routeId("mqtt-producer")
            .startupOrder(2)
            .process(exchange -> {
                String json = exchange.getMessage().getBody(String.class);
                exchange.getMessage().setBody(
                    json.getBytes(StandardCharsets.UTF_8)
                );
            })
            .to("paho:{{mqtt.topic}}"
                + "?clientId={{mqtt.client-id}}-producer"
                + "&qos=1"
                + "&cleanSession=true"
                + "&retained=false")
            .log("Published MQTT measurement");

        // Demo: generate a measurement every five seconds.
//        from("timer:measurement?delay=2000&period=5000")
//            .routeId("demo-measurement")
//            .startupOrder(3)
//            .process(exchange -> {
//                String json = """
//                    {
//                      "deviceId": "demo-sensor",
//                      "timestamp": "%s",
//                      "temperature": 21.5,
//                      "humidity": 62.0
//                    }
//                    """.formatted(Instant.now());
//
//                exchange.getMessage().setBody(json);
//            })
//            .to("direct:publishMeasurement");
    }
}