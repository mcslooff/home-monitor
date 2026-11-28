package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;

@Entity
@Table(name = "mqtt_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class MqttIdentityEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.BOOLEAN)
	private boolean enabled;

	@Column(name = "principal_kind", nullable = false, length = 16)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private Kind kind;

	@Column(name = "device_id", length = 64)
	private String deviceId;

	@Column(name = "credential_generation", nullable = false)
	private long credentialGeneration;
}
