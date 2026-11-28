package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_administration_lock")
public class UserAdministrationLockEntity {
	@Id
	private Short id;

	protected UserAdministrationLockEntity() {
	}
}
