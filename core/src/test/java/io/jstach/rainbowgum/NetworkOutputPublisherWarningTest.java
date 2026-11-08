package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.output.ListLogOutput;

class NetworkOutputPublisherWarningTest {

	private static final String WARNING = """
			Route 'remote' sends appender '%s' to a network output with a synchronous publisher. Network I/O can block the logging thread. Consider an asynchronous publisher, for example logging.route.remote.publisher.uri=async:///.
			"""
		.strip();

	enum Scenario {

		SYNC_NETWORK(true, 1, false), SYNC_LOCAL(true, 0, true), SYNC_MIXED(true, 2, true),
		ASYNC_NETWORK(false, 1, false), ASYNC_MIXED(false, 2, true);

		final boolean synchronous;

		final int networkOutputs;

		final boolean localOutput;

		Scenario(boolean synchronous, int networkOutputs, boolean localOutput) {
			this.synchronous = synchronous;
			this.networkOutputs = networkOutputs;
			this.localOutput = localOutput;
		}

	}

	@ParameterizedTest
	@EnumSource(Scenario.class)
	void warnsForEachSynchronousNetworkOutputWithoutInspectingItsUri(Scenario scenario) {
		var config = LogConfig.builder().build();
		var local = new ListLogOutput();
		List<NetworkListOutput> network = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		var gum = RainbowGum.builder(config).route("remote", r -> {
			if (!scenario.synchronous) {
				r.publisher(PublisherFactory.async().build());
			}
			if (scenario.localOutput) {
				r.appender("local", a -> a.output(local));
			}
			for (int i = 1; i <= scenario.networkOutputs; i++) {
				String name = "network" + i;
				var output = new NetworkListOutput();
				network.add(output);
				r.appender(name, a -> a.output(output));
				if (scenario.synchronous) {
					expected.add(WARNING.formatted(name));
				}
			}
		}).build();
		try (var running = gum.start()) {
			assertEquals(expected,
					config.alerts()
						.dump()
						.stream()
						.filter(a -> a.level() == Level.WARNING)
						.map(LogEvent::message)
						.toList());
			running.log(TestLogEventFactory.of().event("delivered"));
		}
		for (var output : network) {
			assertEquals(1, output.events().size());
			assertEquals("delivered", output.events().getFirst().getKey().message());
		}
		if (scenario.localOutput) {
			assertEquals(1, local.events().size());
		}
	}

	static final class NetworkListOutput extends ListLogOutput {

		@Override
		public OutputType type() {
			return OutputType.NETWORK;
		}

		@Override
		public URI uri() {
			throw new AssertionError("Output URIs must not be inspected for publisher warnings.");
		}

	}

}
