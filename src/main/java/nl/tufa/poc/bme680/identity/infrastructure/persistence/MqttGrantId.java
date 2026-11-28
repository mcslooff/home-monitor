package nl.tufa.poc.bme680.identity.infrastructure.persistence;

import java.io.Serializable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class MqttGrantId implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(name = "identity_id", nullable = false)
	private long identityId;

	@Column(nullable = false, length = 255)
	private String topic;

	@Column(nullable = false, length = 16)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private Permission permission;
}
