package nl.tufa.poc.bme680.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StationDto {
	
	@JsonProperty(required = false)
	@Size(max=64)
	@Pattern(regexp = "[a-zA-Z0-9]{1,64}")
	private String id;
	
	@JsonProperty(required = true)
	@NotNull
	@NotEmpty
	@Size(max=64)
	@Pattern(regexp = "[a-zA-Z0-9_-]{1,64}")
	private String name;
	
	@Size(max=64)
	@Pattern(regexp = "\\d+\\.\\d+\\.\\d+")
	private String version;
	
	@Valid
	private PowerDto power;
	
}
