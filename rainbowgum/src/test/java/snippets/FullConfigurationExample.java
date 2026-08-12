package snippets;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.Optional;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.FileOutputBuilder;
import io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

public class FullConfigurationExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {
		return builder(config).optional();
	}

	RainbowGum.Builder builder(LogConfig config) {
		return
		// @start region = "fullConfiguration"
		RainbowGum.builder(config) //
			.route("console", r -> {
				r.level(Level.INFO);
				r.level(Level.WARNING, "org.apache.http");
				r.level(Level.WARNING, "io.netty");
				r.level(Level.WARNING, "com.zaxxer.hikari");
				r.appender("console", a -> {
					a.output(LogOutput.ofStandardOut());
					a.encoder(new PatternEncoderBuilder("console").pattern("[%thread] %-5level %logger{36} - %msg%n")
						.fromProperties(config.properties())
						.build());
				});
			}) //
			.route("structured", r -> {
				r.level(Level.DEBUG, "com.mycompany.orders");
				r.publisher(PublisherFactory.async().bufferSize(2048).build());
				r.appender("detailfile", a -> {
					/*
					 * A URI-scheme based encoder like the JSON module's "gelf" is
					 * resolved through the service loader, which is why it is comfortable
					 * to reference from properties (see the properties example above)
					 * without the aggregate rainbowgum module needing to require the JSON
					 * module at compile time. Referencing an optional module's encoder
					 * class directly the way this builder is written would require adding
					 * that module as an explicit dependency.
					 */
					a.output(new FileOutputBuilder("detailfile").fileName("./logs/orders-detail.log").build());
					a.encoder(new PatternEncoderBuilder("detailfile")
						.pattern("%d{ISO8601} [%thread] %-5level %logger{50} - %msg%n")
						.fromProperties(config.properties())
						.build());
					a.flags(EnumSet.of(AppenderFlag.REUSE_BUFFER));
				});
			}) //
			.route("errors", r -> {
				r.level(Level.ERROR);
				r.appender("errorfile", a -> {
					a.output(new FileOutputBuilder("errorfile").fileName("./logs/orders-error.log").build());
					a.encoder(new PatternEncoderBuilder("errorfile")
						.pattern("%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n%ex")
						.fromProperties(config.properties())
						.build());
					a.flags(EnumSet.of(AppenderFlag.IMMEDIATE_FLUSH));
				});
			});
		// @end
	}

}
