package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import nl.tufa.poc.bme680.identity.application.port.MqttAdministrationTransaction;

@Component
public class JpaMqttAdministrationTransaction implements MqttAdministrationTransaction {
	private final TransactionTemplate transaction;
	private final SpringDataMqttAdministrationLockRepository lock;

	public JpaMqttAdministrationTransaction(PlatformTransactionManager manager,
			SpringDataMqttAdministrationLockRepository lock) {
		this.transaction = new TransactionTemplate(manager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lock = lock;
	}

	@Override
	public <T> T execute(Supplier<T> work) {
		return transaction.execute(status -> {
			lock.acquire().orElseThrow(() -> new IllegalStateException("MQTT administration lock is missing"));
			return work.get();
		});
	}
}
