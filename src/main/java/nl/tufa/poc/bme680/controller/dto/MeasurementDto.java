package nl.tufa.poc.bme680.controller.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeasurementDto {
	
	@Valid
	private MeasurementValueDto temperature;
	
	@Valid
	private MeasurementValueDto airPressure;
	
	@Valid
	private MeasurementValueDto humidity;
	
	@Valid
	private MeasurementValueDto gasResistance;
	
}
