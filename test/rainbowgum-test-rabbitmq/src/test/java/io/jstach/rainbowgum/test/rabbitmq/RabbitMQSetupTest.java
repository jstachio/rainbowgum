package io.jstach.rainbowgum.test.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.rabbitmq.RabbitMQOutput;
import io.jstach.rainbowgum.rabbitmq.RabbitMQOutputBuilder;

class RabbitMQSetupTest {

	static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"));

	static MyConsumer consumer;

	@BeforeAll
	public static void beforeAll() {
		rabbit.start();
		ConnectionFactory factory = new ConnectionFactory();
		try {
			factory.setUri(rabbit.getAmqpUrl());
			Channel channel = factory.newConnection().createChannel();
			String queue = "rainbowgum";
			String exchange = RabbitMQOutput.DEFAULT_EXCHANGE;
			channel.exchangeDeclare(exchange, RabbitMQOutput.DEFAULT_EXCHANGE_TYPE);
			channel.queueDeclare(queue, false, false, false, Map.of());
			channel.queueBind(queue, exchange, "#");
			consumer = new MyConsumer(channel);
			channel.basicConsume(queue, true, consumer);
		}
		catch (Exception e) {
			fail(e);
		}

	}

	@AfterAll
	public static void afterAll() {
		rabbit.stop();
	}

	static class MyConsumer extends DefaultConsumer {

		BlockingQueue<Message> messages = new ArrayBlockingQueue<>(100);

		public MyConsumer(Channel channel) {
			super(channel);
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
				throws IOException {
			var message = new Message(new String(body, StandardCharsets.UTF_8));
			System.out.println(message);
			messages.add(message);

		}

		public List<Message> blockAndDrain() throws InterruptedException {
			List<Message> r = new ArrayList<>();
			var m = messages.poll(5, TimeUnit.SECONDS);
			if (m != null) {
				r.add(m);
			}
			messages.drainTo(r);
			return r;
		}

		record Message(String body) {
		}

	}

	@Test
	void testMain() throws InterruptedException {
		RabbitMQOutputBuilder b = new RabbitMQOutputBuilder("amqp");
		// b.host(rabbit.getHost());
		// b.port(rabbit.getAmqpPort());
		b.uri(URI.create(rabbit.getAmqpUrl()));
		b.declareExchange(true);
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put(LogProperties.APPENDERS_PROPERTY, "amqp");
		Property.builder().buildWithName(LogAppender.APPENDER_OUTPUT_PROPERTY, "amqp").set("amqp", properties::put);
		b.toProperties(properties::put);
		System.out.println(properties);
		System.out.println(rabbit.getAmqpUrl());
		// LoggerFactoryFriend.reset();
		RabbitMQSetup.run(properties);
		var messages = consumer.blockAndDrain();
		assertEquals(1, messages.size());
		var m = messages.get(0);
		assertTrue(m.toString().contains("io.jstach.rainbowgum.test.rabbitmq.RabbitMQSetup - hello"));
	}

}
