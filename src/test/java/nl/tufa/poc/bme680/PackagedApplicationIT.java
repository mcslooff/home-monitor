package nl.tufa.poc.bme680;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class PackagedApplicationIT {

	private static DatabaseFixture database;
	private static final String BOOTSTRAP_USERNAME = "packaged-" + UUID.randomUUID();
	private static final String BOOTSTRAP_PASSWORD = "test-" + UUID.randomUUID();
	private static final String DEVICE_ID = "packaged" + UUID.randomUUID().toString().replace("-", "");

	@BeforeAll
	static void startDatabase() {
		database = DatabaseFixture.open();
	}

	@AfterAll
	static void stopDatabase() {
		if (database != null) {
			try {
				database.removeHttpTestUser(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
				database.removeMqttTestIdentities(DEVICE_ID);
			} finally {
				database.close();
			}
		}
	}

	@TempDir
	Path workingDirectory;

	@Test
	@Timeout(120)
	void executableJarHonorsArgumentsAndProcessesMqtt() throws Exception {
		Path jar = Path.of(System.getProperty("application.jar")).toAbsolutePath();
		Path log = workingDirectory.resolve("application.log");
		assertTrue(Files.isRegularFile(jar), "Run this test with mvn verify to build the JAR first");
		int mqttPort = availablePort();
		String brokerUrl = "tcp://127.0.0.1:" + mqttPort;
		String deviceId = DEVICE_ID;
		ProcessBuilder builder = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-jar", jar.toString(),
				"--spring.config.location=classpath:/application.yml",
				"--server.address=127.0.0.1", "--server.port=0",
				"--mqtt.broker.host=127.0.0.1", "--mqtt.broker.port=" + mqttPort,
				"--mqtt.client-id=packaged-" + UUID.randomUUID(),
				"--mqtt.topic=sensors/" + deviceId + "/measur",
				"--mqtt.producer.enabled=false",
				"--camel.component.paho.broker-url=" + brokerUrl)
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile());
		isolateEnvironment(builder);
		database.configure(builder);
		builder.environment().put("BOOTSTRAP_ADMIN_USERNAME", BOOTSTRAP_USERNAME);
		builder.environment().put("BOOTSTRAP_ADMIN_PASSWORD", BOOTSTRAP_PASSWORD);

		Process application = builder.start();
		try {
			awaitLog(application, log, "Started App in");
			assertFalse(readLog(log).contains("telemetry.moquette"));
			assertFalse(readLog(log).contains(".moquette_uuid"));
			assertFalse(Files.exists(workingDirectory.resolve("data")));
			var provisioningPort = Pattern.compile("Tomcat started on port (\\d+)").matcher(readLog(log));
			assertTrue(provisioningPort.find(), () -> readLog(log));
			String api = "http://127.0.0.1:" + provisioningPort.group(1);
			var device = provision(api, "/devices", "{\"username\":\"" + deviceId + "\",\"deviceId\":\"" + deviceId + "\"}");
			var service = provision(api, "/services", "{\"username\":\"" + deviceId + "-backend\"}");
			application.destroy();
			assertTrue(application.waitFor(20, TimeUnit.SECONDS));
			builder.command().removeIf(argument -> argument.startsWith("--mqtt.client-id=")
					|| argument.startsWith("--mqtt.producer.enabled="));
			builder.command().addAll(java.util.List.of(
					"--mqtt.consumer.enabled=true", "--mqtt.producer.enabled=true",
					"--mqtt.client-id=" + service.identity().username() + ":v1",
					"--camel.component.paho.user-name=" + service.identity().username(),
					"--camel.component.paho.password=" + service.secret()));
			application = builder.start();
			awaitLog(application, log, "Started App in");
			assertTrue(readLog(log).contains("host=127.0.0.1, port=" + mqttPort),
					() -> readLog(log));
			var httpPortMatcher = Pattern.compile("Tomcat started on port (\\d+)").matcher(readLog(log));
			assertTrue(httpPortMatcher.find(), () -> readLog(log));
			int httpPort = Integer.parseInt(httpPortMatcher.group(1));
			assertTrue(httpPort > 0 && httpPort != 8180, "Expected an ephemeral HTTP port");
			assertTrue(readLog(log).contains("Started mqtt-consumer"), () -> readLog(log));
			assertTrue(readLog(log).contains("Started mqtt-producer"), () -> readLog(log));
			assertFalse(readLog(log).contains("multiple SLF4J providers"), () -> readLog(log));

			try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
				var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + "/bme680"))
						.timeout(Duration.ofSeconds(5))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString("{}")).build();
				assertEquals(401, http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
			}

			try (MqttClient anonymous = new MqttClient(brokerUrl, "anonymous", new MemoryPersistence())) {
				assertThrows(org.eclipse.paho.client.mqttv3.MqttException.class, () -> anonymous.connect());
			}
			try (MqttClient client = new MqttClient(brokerUrl, device.identity().clientIds().getFirst(),
					new MemoryPersistence())) {
				MqttConnectOptions options = new MqttConnectOptions();
				options.setConnectionTimeout(5);
				options.setCleanSession(true);
				options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
				options.setUserName(device.identity().username());
				options.setPassword(device.secret().toCharArray());
				client.setTimeToWait(5000);
				client.connect(options);
				try {
					String payload = """
							{
							  "timestamp": 1795478400,
							  "station": {
							    "id": "%s", "name": "Packaged smoke test",
							    "power": {"voltage": 3.3}
							  },
							  "measurement": {
							    "temperature": {"value": 21.5},
							    "airPressure": {"value": 1013},
							    "humidity": {"value": 50},
							    "gasResistance": {"value": 100}
							  }
							}
							""".formatted(deviceId);
					client.publish("sensors/" + deviceId + "/measur", payload.getBytes(StandardCharsets.UTF_8), 1, false);
					// Observe the normal service boundary, not just the broker's PUBACK.
					awaitLog(application, log, "deviceId=" + deviceId);
				} finally {
					client.disconnect();
				}
			}
		} finally {
			application.destroy();
			if (!application.waitFor(15, TimeUnit.SECONDS)) {
				application.destroyForcibly();
				application.waitFor(5, TimeUnit.SECONDS);
			}
		}
		assertFalse(application.isAlive(), () -> readLog(log));
		assertTrue(readLog(log).contains("shutdown in"), () -> readLog(log));
		assertTrue(readLog(log).contains("Moquette integration has been stopped"), () -> readLog(log));
		// A terminated broker must release the port for another application/test.
		try (ServerSocket socket = new ServerSocket(mqttPort, 1, InetAddress.getByName("127.0.0.1"))) {
			assertEquals(mqttPort, socket.getLocalPort());
		}
	}

	private static nl.tufa.poc.bme680.identity.application.IssuedMqttCredential provision(
			String api, String path, String body) throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			var response = http.send(HttpRequest.newBuilder(URI.create(api + "/admin/mqtt-identities" + path))
					.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
							(BOOTSTRAP_USERNAME + ":" + BOOTSTRAP_PASSWORD).getBytes(StandardCharsets.UTF_8)))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(201, response.statusCode());
			return tools.jackson.databind.json.JsonMapper.builder().build().readValue(response.body(),
					nl.tufa.poc.bme680.identity.application.IssuedMqttCredential.class);
		}
	}

	@Test
	@Timeout(45)
	void databaseFailureStopsStartupBeforeMqttOrHttpAcceptConnections() throws Exception {
		Path log = workingDirectory.resolve("database-failure.log");
		ProcessBuilder builder = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-jar", Path.of(System.getProperty("application.jar")).toAbsolutePath().toString(),
				"--spring.config.location=classpath:/application.yml",
				"--server.address=127.0.0.1", "--server.port=0",
				"--mqtt.broker.host=127.0.0.1", "--mqtt.broker.port=0",
				"--spring.datasource.url=jdbc:mariadb://127.0.0.1:1/unavailable?connectTimeout=1000",
				"--spring.datasource.username=unavailable", "--spring.datasource.password=unavailable",
				"--spring.datasource.hikari.connection-timeout=1000")
				.directory(workingDirectory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
		isolateEnvironment(builder);
		Process application = builder.start();
		try {
			assertTrue(application.waitFor(30, TimeUnit.SECONDS), () -> readLog(log));
			assertTrue(application.exitValue() != 0, () -> readLog(log));
			assertTrue(readLog(log).contains("APPLICATION FAILED") || readLog(log).contains("Application run failed"),
					() -> readLog(log));
			assertFalse(readLog(log).contains("Server bound to host="), () -> readLog(log));
			assertFalse(readLog(log).contains("Tomcat started on port"), () -> readLog(log));
		} finally {
			application.destroyForcibly();
			application.waitFor(5, TimeUnit.SECONDS);
		}
	}

	private static void isolateEnvironment(ProcessBuilder builder) {
		// Keep developer/CI configuration and credentials out of this child application.
		builder.environment().keySet().removeIf(key -> key.startsWith("SPRING_")
				|| key.startsWith("BOOTSTRAP_")
				|| key.startsWith("DB_") || key.startsWith("MQTT_") || key.startsWith("CAMEL_")
				|| key.startsWith("SERVER_") || key.startsWith("LOGGING_")
				|| key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS")
				|| key.equals("_JAVA_OPTIONS"));
		// Also stay on loopback if argument forwarding regresses.
		builder.environment().put("SERVER_ADDRESS", "127.0.0.1");
		builder.environment().put("MQTT_BROKER_HOST", "127.0.0.1");
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			return socket.getLocalPort();
		}
	}

	private static void awaitLog(Process application, Path log, String text) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
		while (application.isAlive() && System.nanoTime() < deadline) {
			if (readLog(log).contains(text)) {
				return;
			}
			Thread.sleep(100);
		}
		assertTrue(readLog(log).contains(text), () -> "Missing log entry: " + text + "\n" + readLog(log));
	}

	private static String readLog(Path log) {
		try {
			return Files.readString(log);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
