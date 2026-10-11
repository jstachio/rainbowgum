package io.jstach.rainbowgum.test.otlpcollector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

record CollectorProcess(Process process, URI endpoint, Path logs, Path data) implements AutoCloseable {

	static CollectorProcess start(Path directory) throws IOException, InterruptedException {
		String binary = System.getProperty("otelcol.binary", "");
		if (binary.isBlank() || !Files.isExecutable(Path.of(binary))) {
			throw new IllegalArgumentException("Set -Dotelcol.binary to an executable otelcol-contrib, "
					+ "or run bin/test-otlp-collector.sh to download the pinned collector");
		}
		Files.createDirectories(directory);
		Path config = directory.resolve("collector.yaml");
		try (var resource = CollectorProcess.class.getResourceAsStream("/collector.yaml")) {
			if (resource == null) {
				throw new IOException("Missing collector.yaml test resource");
			}
			Files.copy(resource, config);
		}
		Path logs = directory.resolve("collector.log");
		Path data = directory.resolve("logs.pb");
		Process process;
		int otlpPort;
		int healthPort;
		// Reserve different ephemeral ports together, then release them for the
		// collector.
		try (var otlp = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
				var health = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			otlpPort = otlp.getLocalPort();
			healthPort = health.getLocalPort();
		}
		var builder = new ProcessBuilder(binary, "--config=" + config.toAbsolutePath());
		builder.environment().put("RAINBOWGUM_TEST_OTLP_PORT", Integer.toString(otlpPort));
		builder.environment().put("RAINBOWGUM_TEST_HEALTH_PORT", Integer.toString(healthPort));
		builder.environment().put("RAINBOWGUM_TEST_OTLP_FILE", data.toAbsolutePath().toString());
		builder.environment()
			.put("RAINBOWGUM_TEST_OTLP_JSON_FILE", directory.resolve("logs.json").toAbsolutePath().toString());
		process = builder.redirectErrorStream(true).redirectOutput(logs.toFile()).start();
		var collector = new CollectorProcess(process, URI.create("http://127.0.0.1:" + otlpPort + "/v1/logs"), logs,
				data);
		try {
			collector.awaitReady(URI.create("http://127.0.0.1:" + healthPort + "/"));
			return collector;
		}
		catch (IOException | InterruptedException | RuntimeException | Error e) {
			try {
				collector.close();
			}
			catch (Exception closeFailure) {
				e.addSuppressed(closeFailure);
			}
			throw e;
		}
	}

	private void awaitReady(URI health) throws IOException, InterruptedException {
		try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
			var request = HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(1)).build();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			while (process.isAlive() && System.nanoTime() < deadline) {
				try {
					if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
						return;
					}
				}
				catch (IOException e) {
					// The health endpoint is not accepting connections yet.
				}
				Thread.sleep(50);
			}
		}
		throw new IOException("Collector failed to become ready. Logs:\n" + Files.readString(logs));
	}

	@Override
	public void close() throws InterruptedException, IOException {
		process.destroy();
		try {
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				throw new IOException("Collector did not stop gracefully. Logs:\n" + Files.readString(logs));
			}
			if (process.exitValue() != 0) {
				throw new IOException(
						"Collector exited with " + process.exitValue() + ". Logs:\n" + Files.readString(logs));
			}
		}
		finally {
			if (process.isAlive()) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
			}
		}
	}

}
