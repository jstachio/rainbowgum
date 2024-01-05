package io.jstach.rainbowgum.test.rabbitmq;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.rabbitmq.RabbitMQOutputBuilder;

class RabbitMQSetupTest {

	static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"));

	@BeforeAll
	public static void beforeAll() {
		rabbit.start();
	}

	@AfterAll
	public static void afterAll() {
		rabbit.stop();
	}

	@Test
	void testMain() {
		RabbitMQOutputBuilder b = new RabbitMQOutputBuilder("amqp");
		//b.host(rabbit.getHost());
		//b.port(rabbit.getAmqpPort());
		b.uri(URI.create(rabbit.getAmqpUrl()));
		b.declareExchange(true);
		Map<String, String> properties = b.asProperties();
		properties.put(LogProperties.OUTPUT_PROPERTY, "amqp");
		properties.put(LogProperties.OUTPUT_PROPERTY + ".amqp", "amqp:///");
		System.out.println(properties);
		System.out.println(rabbit.getAmqpUrl());
		//LoggerFactoryFriend.reset();
		RabbitMQSetup.run(properties);
	}

}
