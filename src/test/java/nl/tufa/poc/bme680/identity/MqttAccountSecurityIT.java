package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import nl.tufa.poc.bme680.App;
import nl.tufa.poc.bme680.DatabaseFixture;
import nl.tufa.poc.bme680.identity.application.IssuedMqttCredential;
import nl.tufa.poc.bme680.identity.application.MqttAdministrationService;
import nl.tufa.poc.bme680.identity.application.UserAdministrationService;
import nl.tufa.poc.bme680.identity.application.port.MqttAdministrationTransaction;
import nl.tufa.poc.bme680.identity.application.port.MqttSecretGenerator;
import nl.tufa.poc.bme680.identity.application.port.PasswordHasher;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant;
import nl.tufa.poc.bme680.identity.domain.model.MqttGrant.Permission;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.IdentityConstraintProbeRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.SpringDataHttpUserRepository;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class MqttAccountSecurityIT {
	private final String prefix = "mqtt" + UUID.randomUUID().toString().replace("-", "");
	private final String admin = prefix + "admin";
	private final String adminPassword = "test-" + UUID.randomUUID();
	private final JsonMapper json = JsonMapper.builder().build();
	private DatabaseFixture database;
	private ConfigurableApplicationContext context;
	private MockMvc mvc;
	private MqttIdentityRepository identities;

	@BeforeAll
	void start() throws Exception {
		database = DatabaseFixture.open();
		int port;
		try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			port = socket.getLocalPort();
		}
		context = new SpringApplicationBuilder(App.class).web(WebApplicationType.SERVLET)
				.initializers(application -> TestPropertyValues.of(database.springProperties()).and(
						"server.address=127.0.0.1", "server.port=0",
						"mqtt.broker.host=127.0.0.1", "mqtt.broker.port=" + port,
						"mqtt.client-id=management-" + UUID.randomUUID(),
						"mqtt.producer.enabled=false",
						"camel.component.paho.broker-url=tcp://127.0.0.1:" + port,
						"BOOTSTRAP_ADMIN_USERNAME=" + admin, "BOOTSTRAP_ADMIN_PASSWORD=" + adminPassword)
						.applyTo(application)).run();
		mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
		identities = context.getBean(MqttIdentityRepository.class);
	}

	@AfterAll
	void close() {
		try {
			if (context != null) {
				var probes = context.getBean(IdentityConstraintProbeRepository.class);
				probes.deleteGrants(prefix + "%");
				probes.deleteMqttIdentities(prefix + "%");
				var users = context.getBean(SpringDataHttpUserRepository.class);
				users.findAll().stream().filter(user -> user.getUsername().startsWith(prefix))
						.forEach(user -> users.deleteById(user.getId()));
			}
		} finally {
			if (context != null) {
				context.close();
			}
			if (database != null) {
				database.close();
			}
		}
	}

	@Test
	void onlyAdministratorsCanProvisionInspectRotateOrDisableAndIdsCannotEscapeTopics() throws Exception {
		var users = context.getBean(UserAdministrationService.class);
		users.create(prefix + "user", "test-user-secret", true, Set.of(Role.USER));
		users.create(prefix + "sensor", "test-sensor-secret", true, Set.of(Role.SENSOR));
		mvc.perform(get("/admin/mqtt-identities")).andExpect(status().isUnauthorized());
		for (String role : List.of("user", "sensor")) {
			String password = "test-" + role + "-secret";
			mvc.perform(get("/admin/mqtt-identities").with(httpBasic(prefix + role, password)))
					.andExpect(status().isForbidden());
			mvc.perform(post("/admin/mqtt-identities/devices").with(httpBasic(prefix + role, password))
							.contentType("application/json").content(deviceBody(prefix + "denied", prefix + "denied")))
					.andExpect(status().isForbidden());
			mvc.perform(post("/admin/mqtt-identities/services").with(httpBasic(prefix + role, password))
							.contentType("application/json").content("{\"username\":\"denied\"}"))
					.andExpect(status().isForbidden());
			mvc.perform(post("/admin/mqtt-identities/1/credentials").with(httpBasic(prefix + role, password))
							.contentType("application/json").content("{}")).andExpect(status().isForbidden());
			mvc.perform(put("/admin/mqtt-identities/1/enabled").with(httpBasic(prefix + role, password))
							.contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isForbidden());
		}
		mvc.perform(post("/admin/mqtt-identities/devices").contentType("application/json")
						.content(deviceBody("unknown", "unknown"))).andExpect(status().isUnauthorized());
		for (String id : List.of("room/other", "+", "#", "$SYS", "..", "x".repeat(65))) {
			mvc.perform(post("/admin/mqtt-identities/devices").with(httpBasic(admin, adminPassword))
							.contentType("application/json").content(deviceBody(prefix + "invalid", id)))
					.andExpect(status().isBadRequest());
		}
		var first = device(prefix + "first", prefix + "first");
		var second = device(prefix + "second", prefix + "second");
		assertNotEquals(first.secret(), second.secret());
		assertEquals(List.of(new MqttGrant(first.identity().id(), "sensors/" + prefix + "first/measur", Permission.PUBLISH)),
				first.identity().grants());
		assertNotEquals(first.identity().grants().getFirst().topic(), second.identity().grants().getFirst().topic());
		mvc.perform(post("/admin/mqtt-identities/devices").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content(deviceBody(prefix + "first", prefix + "other")))
				.andExpect(status().isConflict());
		mvc.perform(post("/admin/mqtt-identities/devices").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content(deviceBody(prefix + "other", prefix + "first")))
				.andExpect(status().isConflict());
		mvc.perform(put("/admin/mqtt-identities/" + first.identity().id() + "/grants").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{}")).andExpect(status().isForbidden());
		mvc.perform(post("/admin/mqtt-identities/services").with(httpBasic(admin, adminPassword))
						.contentType("text/plain").content("{}")).andExpect(status().isForbidden());
		mvc.perform(post("/admin/mqtt-identities/services").with(httpBasic(admin, adminPassword)).with(csrf())
						.contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void rotationAndDisablePersistWithoutLeakingSecretsOrEnablingADisabledDevice(CapturedOutput output) throws Exception {
		var issued = device(prefix + "lifecycle", prefix + "lifecycle");
		long id = issued.identity().id();
		var encoder = context.getBean(PasswordEncoder.class);
		var original = identities.findById(id).orElseThrow();
		assertTrue(encoder.matches(issued.secret(), original.passwordHash().encoded()));
		var disabled = mvc.perform(put("/admin/mqtt-identities/" + id + "/enabled").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertFalse(disabled.contains("secret"));
		assertFalse(disabled.contains("password"));
		assertFalse(identities.findById(id).orElseThrow().enabled());
		var response = mvc.perform(post("/admin/mqtt-identities/" + id + "/credentials").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{}")).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		var rotated = json.readValue(response, IssuedMqttCredential.class);
		var current = identities.findById(id).orElseThrow();
		assertFalse(current.enabled());
		assertNotEquals(issued.secret(), rotated.secret());
		assertTrue(encoder.matches(rotated.secret(), current.passwordHash().encoded()));
		assertFalse(encoder.matches(issued.secret(), current.passwordHash().encoded()));
		assertEquals(issued.identity().grants(), rotated.identity().grants());
		String list = mvc.perform(get("/admin/mqtt-identities").with(httpBasic(admin, adminPassword)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertFalse(list.contains("\"secret\""));
		assertFalse(list.contains("password"));
		assertFalse(list.contains(issued.secret()));
		assertFalse(list.contains(rotated.secret()));
		assertFalse(output.getAll().contains(issued.secret()));
		assertFalse(output.getAll().contains(rotated.secret()));
		assertFalse(output.getAll().contains(current.passwordHash().encoded()));
		mvc.perform(put("/admin/mqtt-identities/" + id + "/enabled").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{}")).andExpect(status().isBadRequest());
		mvc.perform(post("/admin/mqtt-identities/-1/credentials").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{}")).andExpect(status().isNotFound());
	}

	@Test
	void serviceAccountsReceiveExactSubscriptionsRegardlessOfCreationOrderAndNeverAuthenticateHttp() throws Exception {
		var backend = service(admin); // Same name as HTTP admin, but an independent credential domain.
		assertTrue(backend.identity().grants().stream().allMatch(grant -> grant.permission() == Permission.SUBSCRIBE));
		assertTrue(context.getBean(CamelContext.class).getRouteController().getRouteStatus("mqtt-producer").isStopped());
		var later = device(prefix + "later", prefix + "later");
		assertTrue(identities.findGrants(backend.identity().id()).contains(
				new MqttGrant(backend.identity().id(), later.identity().grants().getFirst().topic(), Permission.SUBSCRIBE)));
		var after = service(prefix + "backendAfter");
		Set<String> topics = identities.findAll().stream().filter(identity -> identity.kind() == Kind.DEVICE)
				.map(identity -> "sensors/" + identity.deviceId() + "/measur").collect(Collectors.toSet());
		assertEquals(topics, after.identity().grants().stream().map(MqttGrant::topic).collect(Collectors.toSet()));
		assertTrue(after.identity().grants().stream().allMatch(grant -> grant.permission() == Permission.SUBSCRIBE));
		mvc.perform(get("/admin/users").with(httpBasic(admin, backend.secret()))).andExpect(status().isUnauthorized());
		var start = new CountDownLatch(1);
		var administration = context.getBean(MqttAdministrationService.class);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var createdDevice = executor.submit(() -> {
				start.await();
				return administration.createDevice(prefix + "raceDevice", prefix + "raceDevice");
			});
			var createdService = executor.submit(() -> {
				start.await();
				return administration.createService(prefix + "raceService");
			});
			start.countDown();
			var device = createdDevice.get(15, TimeUnit.SECONDS);
			var account = createdService.get(15, TimeUnit.SECONDS);
			assertTrue(identities.findGrants(account.identity().id()).contains(new MqttGrant(
					account.identity().id(), device.identity().grants().getFirst().topic(), Permission.SUBSCRIBE)));
		}
	}

	@Test
	void failedGrantProvisioningRollsBackTheDeviceAndItsOwnPublishGrant() throws Exception {
		service(prefix + "failureBackend");
		MqttIdentityRepository failing = mock(MqttIdentityRepository.class, delegatesTo(identities));
		doThrow(new IllegalStateException("Injected grant failure")).when(failing)
				.addGrant(argThat(grant -> grant.permission() == Permission.SUBSCRIBE));
		var administration = new MqttAdministrationService(failing, context.getBean(PasswordHasher.class),
				context.getBean(MqttSecretGenerator.class), context.getBean(MqttAdministrationTransaction.class), Optional.empty());
		assertThrows(IllegalStateException.class, () ->
				administration.createDevice(prefix + "rollback", prefix + "rollback"));
		assertTrue(identities.findByUsername(prefix + "rollback").isEmpty());
		assertTrue(identities.findByDeviceId(prefix + "rollback").isEmpty());
	}

	private IssuedMqttCredential device(String username, String deviceId) throws Exception {
		var response = mvc.perform(post("/admin/mqtt-identities/devices").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content(deviceBody(username, deviceId)))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		assertFalse(response.contains("password"));
		var issued = json.readValue(response, IssuedMqttCredential.class);
		assertTrue(issued.secret().matches("[A-Za-z0-9_-]{43}"));
		return issued;
	}

	private IssuedMqttCredential service(String username) throws Exception {
		var response = mvc.perform(post("/admin/mqtt-identities/services").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"username\":\"" + username + "\"}"))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		return json.readValue(response, IssuedMqttCredential.class);
	}

	private static String deviceBody(String username, String deviceId) {
		return "{\"username\":\"" + username + "\",\"deviceId\":\"" + deviceId + "\"}";
	}
}
