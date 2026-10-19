package nl.tufa.poc.bme680.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bme680Measurement {

	private String deviceId;
	
	private String stationName;
	
	private Date timestamp;
	
	// Value in Celcius
	private Double temperature;
	
	// Value in %
	private Double  humidity;
	
	// Value in hPa
	private Double  airpressure;
	
	// Value in kOhm
	private Double voc;
	
	// value in Volt
	private Double battery;
	
	@EqualsAndHashCode.Exclude
	@Builder.Default
	private final Date receivedAt = new Date();
	
}
