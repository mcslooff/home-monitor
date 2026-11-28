package nl.tufa.poc.bme680.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import nl.tufa.poc.bme680.App;
import nl.tufa.poc.bme680.DatabaseFixture;
import nl.tufa.poc.bme680.identity.application.UserAdministrationService;
import nl.tufa.poc.bme680.identity.application.UserManagementException;
import nl.tufa.poc.bme680.identity.domain.model.HttpUser.Role;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity;
import nl.tufa.poc.bme680.identity.domain.model.MqttIdentity.Kind;
import nl.tufa.poc.bme680.identity.domain.model.PasswordHash;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;
import nl.tufa.poc.bme680.identity.domain.port.MqttIdentityRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.SpringDataHttpUserRepository;
import nl.tufa.poc.bme680.identity.infrastructure.persistence.SpringDataMqttIdentityRepository;
import nl.tufa.poc.bme680.security.WebSecurity;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class HttpUserSecurityIT {
	private final String prefix = "web" + UUID.randomUUID().toString().replace("-", "");
	private final String admin = prefix + "admin";
	private final String adminPassword = "test-" + UUID.randomUUID();
	private DatabaseFixture database;
	private ConfigurableApplicationContext context;
	private MockMvc mvc;
	private final JsonMapper json = JsonMapper.builder().build();

	@BeforeAll
	void start() throws Exception {
		database = DatabaseFixture.open();
		openApplication(adminPassword);
	}

	@AfterAll
	void close() {
		try {
			if (context != null) {
				// Only remove this suite's own rows, even with an external test database.
				var mqtt = context.getBean(SpringDataMqttIdentityRepository.class);
				mqtt.findAll().stream().filter(identity -> identity.getUsername().startsWith(prefix))
						.forEach(identity -> mqtt.deleteById(identity.getId()));
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
	void authorizationMatrixAndJsonOnlyCsrfBoundary() throws Exception {
		String sensor = prefix + "sensor";
		String user = prefix + "user";
		create(sensor, "test-sensor-password", "SENSOR");
		create(user, "test-user-password", "USER");
		mvc.perform(post("/bme680").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/bme680").with(httpBasic(sensor, "incorrect")).contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/bme680").with(httpBasic("unknown", "incorrect")).contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/bme680").with(httpBasic(user, "test-user-password")).contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/bme680").with(httpBasic(admin, adminPassword)).contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/bme680").with(httpBasic(sensor, "test-sensor-password"))
						.contentType("application/json").content(measurement()))
				.andExpect(status().isOk());
		mvc.perform(get("/bme680").with(httpBasic(sensor, "test-sensor-password"))).andExpect(status().isForbidden());
		mvc.perform(post("/bme680/nested").with(httpBasic(sensor, "test-sensor-password"))
						.contentType("application/json").content(measurement()))
				.andExpect(status().isForbidden());
		mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/admin/users").with(httpBasic(user, "test-user-password"))).andExpect(status().isForbidden());
		mvc.perform(get("/admin/users").with(httpBasic(sensor, "test-sensor-password"))).andExpect(status().isForbidden());
		mvc.perform(get("/unspecified").with(httpBasic(admin, adminPassword))).andExpect(status().isForbidden());
		mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword))
						.contentType("text/plain").content("{}")).andExpect(status().isForbidden());
		mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword)).with(csrf())
						.contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword))
						.contentType("application/x-www-form-urlencoded").content("username=attacker"))
				.andExpect(status().isForbidden());
		mvc.perform(options("/admin/users").header("Origin", "https://untrusted.invalid")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().is4xxClientError()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		var encoder = new WebSecurity().passwordEncoder();
		context.getBean(MqttIdentityRepository.class).save(new MqttIdentity(null, prefix + "mqtt",
				new PasswordHash(encoder.encode("test-mqtt-password")), true, Kind.SERVICE, null));
		mvc.perform(get("/admin/users").with(httpBasic(prefix + "mqtt", "test-mqtt-password")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void userLifecycleTakesEffectOnTheNextBasicAuthRequestWithoutExposingCredentials(CapturedOutput output) throws Exception {
		String name = prefix + "lifecycle";
		String password = "test-lifecycle-secret";
		long id = create(name, password, "SENSOR");
		var stored = context.getBean(HttpUserRepository.class).findByUsername(name).orElseThrow();
		assertTrue(stored.passwordHash().encoded().startsWith("{bcrypt}"));
		assertFalse(stored.passwordHash().encoded().contains(password));
		mvc.perform(post("/bme680").with(httpBasic(name, password))
						.contentType("application/json").content(measurement())).andExpect(status().isOk());
		mvc.perform(put("/admin/users/" + id + "/roles").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"roles\":[\"USER\"]}")).andExpect(status().isOk());
		mvc.perform(post("/bme680").with(httpBasic(name, password))
						.contentType("application/json").content(measurement())).andExpect(status().isForbidden());
		mvc.perform(put("/admin/users/" + id + "/password").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"password\":\"test-replacement-secret\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/admin/users").with(httpBasic(name, password))).andExpect(status().isUnauthorized());
		mvc.perform(get("/admin/users").with(httpBasic(name, "test-replacement-secret"))).andExpect(status().isForbidden());
		mvc.perform(put("/admin/users/" + id + "/enabled").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isOk());
		mvc.perform(get("/admin/users").with(httpBasic(name, "test-replacement-secret"))).andExpect(status().isUnauthorized());
		var response = mvc.perform(get("/admin/users").with(httpBasic(admin, adminPassword)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertFalse(response.contains("password"));
		assertFalse(response.contains("{bcrypt}"));
		assertFalse(response.contains(password));
		var invalid = mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("""
								{"username":"invalid", "password":"secret", "enabled":true, "roles":["USER"]}
								""")).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		assertFalse(invalid.contains("secret"));
		mvc.perform(put("/admin/users/" + id + "/enabled").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{}")).andExpect(status().isBadRequest());
		mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("""
								{"username":"%s", "password":"test-duplicate-secret", "enabled":true, "roles":["USER"]}
								""".formatted(name))).andExpect(status().isConflict());
		assertFalse(output.getAll().contains(password));
		assertFalse(output.getAll().contains("test-replacement-secret"));
		assertFalse(output.getAll().contains(stored.passwordHash().encoded()));
		assertFalse(output.getAll().contains(adminPassword));
	}

	@Test
	void lastAdministratorCannotBeDisabledOrDemotedEvenByConcurrentMutations() throws Exception {
		var service = context.getBean(UserAdministrationService.class);
		long adminId = context.getBean(HttpUserRepository.class).findByUsername(admin).orElseThrow().id();
		mvc.perform(put("/admin/users/" + adminId + "/roles").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"roles\":[\"USER\"]}")).andExpect(status().isConflict());
		mvc.perform(put("/admin/users/" + adminId + "/enabled").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isConflict());
		var second = service.create(prefix + "secondadmin", "test-second-admin-secret", true, Set.of(Role.ADMIN));
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> disableAtOnce(service, adminId, start));
			var other = executor.submit(() -> disableAtOnce(service, second.id(), start));
			start.countDown();
			int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (other.get(15, TimeUnit.SECONDS) ? 1 : 0);
			assertEquals(1, successes);
			assertEquals(1, context.getBean(HttpUserRepository.class).countEnabledAdministrators());
		} finally {
			service.setEnabled(adminId, true);
			service.setEnabled(second.id(), false);
		}
	}

	@Test
	void restartingWithDifferentBootstrapCredentialsDoesNotResetTheExistingAdministrator() throws Exception {
		context.close();
		openApplication("different-bootstrap-secret");
		mvc.perform(get("/admin/users").with(httpBasic(admin, adminPassword))).andExpect(status().isOk());
		mvc.perform(get("/admin/users").with(httpBasic(admin, "different-bootstrap-secret"))).andExpect(status().isUnauthorized());
		assertEquals(1, context.getBean(HttpUserRepository.class).findAll().stream()
				.filter(user -> user.username().equals(admin)).count());
	}

	private static boolean disableAtOnce(UserAdministrationService service, long id, CountDownLatch start) throws Exception {
		start.await();
		try {
			service.setEnabled(id, false);
			return true;
		} catch (UserManagementException exception) {
			assertEquals(UserManagementException.Reason.CONFLICT, exception.reason());
			return false;
		}
	}

	private long create(String name, String password, String role) throws Exception {
		var response = mvc.perform(post("/admin/users").with(httpBasic(admin, adminPassword))
						.contentType("application/json").content("""
								{"username":"%s", "password":"%s", "enabled":true, "roles":["%s"]}
								""".formatted(name, password, role)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		assertFalse(response.contains("password"));
		assertFalse(response.contains(password));
		return json.readTree(response).get("id").asLong();
	}

	private void openApplication(String bootstrapPassword) throws Exception {
		int mqttPort;
		try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			mqttPort = socket.getLocalPort();
		}
		int port = mqttPort;
		context = new SpringApplicationBuilder(App.class).web(WebApplicationType.SERVLET)
				.initializers(application -> TestPropertyValues.of(database.springProperties()).and(
						"server.address=127.0.0.1", "server.port=0",
						"mqtt.broker.host=127.0.0.1", "mqtt.broker.port=" + port,
						"mqtt.client-id=security-" + UUID.randomUUID(),
						"camel.component.paho.broker-url=tcp://127.0.0.1:" + port,
						"BOOTSTRAP_ADMIN_USERNAME=" + admin, "BOOTSTRAP_ADMIN_PASSWORD=" + bootstrapPassword)
						.applyTo(application)).run();
		mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
	}

	private static String measurement() {
		return """
				{"timestamp":1795478400,
				 "station":{"id":"securitytest","name":"securitytest","power":{"voltage":3.3}},
				 "measurement":{"temperature":{"value":21.5},"airPressure":{"value":1013},
				 "humidity":{"value":50},"gasResistance":{"value":100}}}
				""";
	}
}
