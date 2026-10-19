package nl.tufa.poc.bme680.controller.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PowerDto {
	
	@Min(0)
	@Max(5)
	private BigDecimal voltage;
	
}
