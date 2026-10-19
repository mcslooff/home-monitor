package nl.tufa.poc.bme680.controller.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeasurementValueDto {
	
	private BigDecimal value;
	
	private String unit;
	
}
