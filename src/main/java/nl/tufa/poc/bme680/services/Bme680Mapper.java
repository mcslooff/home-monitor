package nl.tufa.poc.bme680.services;

import java.util.Date;

import org.springframework.stereotype.Service;

import nl.tufa.poc.bme680.controller.dto.Bme680MeasurementDto;
import nl.tufa.poc.bme680.model.Bme680Measurement;

@Service
public class Bme680Mapper {

	public Bme680Measurement map(Bme680MeasurementDto dto) {
		var measurement = dto.getMeasurement();

		return Bme680Measurement.builder().airpressure(measurement.getAirPressure().getValue().doubleValue())
				.battery(dto.getStation().getPower().getVoltage().doubleValue())
				.humidity(measurement.getHumidity().getValue().doubleValue())
				.deviceId((dto.getStation().getId() == null || dto.getStation().getId().isBlank())
						? dto.getStation().getName()
						: dto.getStation().getId())
				.stationName(dto.getStation().getName())
				.temperature(measurement.getTemperature().getValue().doubleValue())
				.timestamp(new Date(dto.getTimestamp() * 1000L))
				.voc(measurement.getGasResistance().getValue().doubleValue()).build();
	}

}
