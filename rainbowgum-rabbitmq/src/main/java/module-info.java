import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Provides RabbitMQ Rainbow Gum output.
 * <!-- START PROPERTIES -->
 * <!-- END PROPERTIES -->
 */
module io.jstach.rainbowgum.rabbitmq {
	exports io.jstach.rainbowgum.rabbitmq;
	
	requires transitive io.jstach.rainbowgum;
	requires static io.jstach.rainbowgum.annotation;
	requires com.rabbitmq.client;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.rabbitmq.RabbitMQInitializer;
}