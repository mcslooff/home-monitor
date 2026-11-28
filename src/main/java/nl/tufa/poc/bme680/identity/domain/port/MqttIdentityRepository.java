package nl.tufa.poc.bme680.identity.domain.port;

import java.util.List;
import java.util.Optional;

import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;

public interface MqttIdentityRepository {
	MqttIdentity save(MqttIdentity identity);

	Optional<MqttIdentity> findByUsername(String username);

	Optional<MqttIdentity> findById(long id);

	Optional<MqttIdentity> findByDeviceId(String deviceId);

	List<MqttIdentity> findAll();

	void addGrant(MqttGrant grant);

	List<MqttGrant> findGrants(long identityId);
}
