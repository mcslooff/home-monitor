package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import nl.tufa.poc.bme680.identity.application.port.UserAdministrationTransaction;

@Component
public class JpaUserAdministrationTransaction implements UserAdministrationTransaction {
	private final TransactionTemplate transaction;
	private final SpringDataUserAdministrationLockRepository lock;

	public JpaUserAdministrationTransaction(PlatformTransactionManager manager,
			SpringDataUserAdministrationLockRepository lock) {
		this.transaction = new TransactionTemplate(manager);
		// Do not inherit an older repeatable-read snapshot from a caller.
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lock = lock;
	}

	@Override
	public <T> T execute(Supplier<T> work) {
		return transaction.execute(status -> {
			lock.acquire().orElseThrow(() -> new IllegalStateException("User administration lock is missing"));
			return work.get();
		});
	}
}
