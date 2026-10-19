package nl.tufa.poc.bme680.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Bme680MeasurementDto {
	
	@JsonProperty(required = true)
	@NotNull
	private Long timestamp;
	
	@Valid
	private StationDto station;
	
	@Valid
	private MeasurementDto measurement;
		
}
