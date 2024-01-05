package io.jstach.rainbowgum.test.rabbitmq;

import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public class RabbitMQSetup {

	public static void main(String[] args) {

		Map<String, String> properties = new LinkedHashMap<>();
		properties.put(LogProperties.OUTPUT_PROPERTY, "amqp");
		properties.put(LogProperties.OUTPUT_PROPERTY + ".amqp", "amqp:///");
		var config = LogConfig.builder().logProperties(properties::get).build();
		RainbowGumServiceProvider.runInitializers(ServiceLoader.load(RainbowGumServiceProvider.class), config);
		RainbowGum.set(() -> RainbowGum.builder(config).build());
		// RainbowGum.set(() -> gum);
		var logger = LoggerFactory.getLogger(RabbitMQSetup.class);
		logger.info("hello");
	}

	public static void run(Map<String, String> properties) {
		var config = LogConfig.builder().logProperties(properties::get).build();
		RainbowGumServiceProvider.runInitializers(ServiceLoader.load(RainbowGumServiceProvider.class), config);
		RainbowGum.set(() -> RainbowGum.builder(config).build());
		RainbowGum.of();
		// RainbowGum.set(() -> gum);
		System.getLogger(RabbitMQSetup.class.getName()).log(Level.INFO, "hello");
		//var logger = LoggerFactory.getLogger(RabbitMQSetup.class);
		//logger.info("hello");
	}

}
