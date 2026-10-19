package nl.tufa.poc.bme680.services;

import org.apache.camel.ProducerTemplate;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@DependsOn("mqttBroker")
public class MeasurementPublisher {

    private final ProducerTemplate producerTemplate;

    public MeasurementPublisher(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public void publish(String json) {
        producerTemplate.sendBody("direct:publishMeasurement", json);
    }
    
//    @Scheduled(fixedDelay = 3000)
//    public void publishSomething() {
//    	publish("Appelsap");
//    }
}