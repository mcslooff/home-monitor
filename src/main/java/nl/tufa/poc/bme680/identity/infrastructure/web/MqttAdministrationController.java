package nl.tufa.poc.bme680.identity.infrastructure.web;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nl.tufa.poc.bme680.identity.application.IssuedMqttCredential;
import nl.tufa.poc.bme680.identity.application.MqttAdministrationService;
import nl.tufa.poc.bme680.identity.application.MqttIdentitySummary;

@RestController
@RequestMapping("/admin/mqtt-identities")
@RequiredArgsConstructor
public class MqttAdministrationController {
	private final MqttAdministrationService identities;

	@GetMapping
	public ResponseEntity<List<MqttIdentitySummary>> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identities.list());
	}

	@PostMapping(value = "/devices", consumes = "application/json")
	public ResponseEntity<IssuedMqttCredential> device(@RequestBody @Valid MqttRequests.Device request) {
		return issued(identities.createDevice(request.username(), request.deviceId()), HttpStatus.CREATED);
	}

	@PostMapping(value = "/services", consumes = "application/json")
	public ResponseEntity<IssuedMqttCredential> service(@RequestBody @Valid MqttRequests.Service request) {
		return issued(identities.createService(request.username()), HttpStatus.CREATED);
	}

	@PostMapping(value = "/{id}/credentials", consumes = "application/json")
	public ResponseEntity<IssuedMqttCredential> rotate(@PathVariable long id, @RequestBody MqttRequests.Rotate request) {
		return issued(identities.rotate(id), HttpStatus.OK);
	}

	@PutMapping(value = "/{id}/enabled", consumes = "application/json")
	public ResponseEntity<MqttIdentitySummary> enabled(@PathVariable long id, @RequestBody @Valid MqttRequests.Enabled request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identities.setEnabled(id, request.enabled()));
	}

	private static ResponseEntity<IssuedMqttCredential> issued(IssuedMqttCredential credential, HttpStatus status) {
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
				.header("Pragma", "no-cache").body(credential);
	}
}
