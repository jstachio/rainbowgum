package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.output.ListLogOutput;

class NetworkOutputPublisherWarningTest {

	@Test
	void networkOutputBehindASynchronousPublisherIsWarned() {
		var config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config)
			.route("remote", r -> r.appender("collector", a -> a.output(new NetworkListOutput())))
			.build()) {
			assertEquals(List.of("Route 'remote' sends appender 'collector' to a network output with a synchronous "
					+ "publisher, so every log call waits for the network. Use an asynchronous publisher, for example "
					+ "logging.route.remote.publisher.uri=async:///."), warnings(config));
		}
	}

	@Test
	void networkOutputBehindAnAsynchronousPublisherIsNotWarned() {
		var config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config)
			.route("remote",
					r -> r.publisher(PublisherFactory.async().build())
						.appender("collector", a -> a.output(new NetworkListOutput())))
			.build()) {
			assertEquals(List.of(), warnings(config));
		}
	}

	private static List<String> warnings(LogConfig config) {
		return config.alerts()
			.dump()
			.stream()
			.filter(a -> a.message().contains("network output"))
			.map(a -> a.message())
			.toList();
	}

	static final class NetworkListOutput extends ListLogOutput {

		@Override
		public OutputType type() {
			return OutputType.NETWORK;
		}

	}

}
