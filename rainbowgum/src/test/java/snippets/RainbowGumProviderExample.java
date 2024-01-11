package snippets;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogPublisher.PublisherProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

// @start region="provider"
class RainbowGumProviderExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		Property<Integer> bufferSize = Property.builder()
			.map(Integer::parseInt)
			.orElse(1024)
			.build("logging.custom.async.bufferSize");

		Property<LogOutput> output = Property.builder()
			.map(URI::create)
			.map(u -> config.output(u, ""))
			.orElse(LogOutput.ofStandardOut())
			.build("logging.custom.output");

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(PublisherProvider.async().bufferSize(bufferSize.get(config.properties()).value()).build());
				r.appender("console", a -> {
					a.output(output.get(config.properties()).value());
				});
				r.level(Level.INFO);
			})
			.build();

		return Optional.of(gum);
	}

}
// @end
