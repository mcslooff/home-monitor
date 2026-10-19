package nl.tufa.poc.bme680.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.tufa.poc.bme680.controller.dto.Bme680MeasurementDto;
import nl.tufa.poc.bme680.services.Bme680Service;

@Slf4j
@RestController
@RequiredArgsConstructor
public class Bme680Controller {

	private final Bme680Service bme680Service;
	
	@PostMapping("/bme680")
	public ResponseEntity<String> receiveMeasurement(@RequestBody @Valid Bme680MeasurementDto value,
			BindingResult bindingResult) {

		if (bindingResult.hasErrors()) {
			List<FieldError> errors = bindingResult.getFieldErrors();
			List<String> message = new ArrayList<>();
			for (FieldError e : errors) {
				message.add(e.getField());
			}
			
			String errorMessage = message.stream().collect(Collectors.joining(","));
			log.error("Field validation errors: {}", errorMessage);
			return ResponseEntity.badRequest().body(errorMessage);
		}

		bme680Service.registerMeasurement(value);
		return ResponseEntity.ok("success");
	}

}
