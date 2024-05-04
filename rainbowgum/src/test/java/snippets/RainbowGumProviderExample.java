package snippets;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

// @start region="provider"
class RainbowGumProviderExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		Property<Integer> bufferSize = Property.builder()
			.map(Integer::parseInt)
			.build("logging.custom.async.bufferSize");

		Property<LogProvider<LogOutput>> output = Property.builder()
			.map(URI::create)
			.mapResult(u -> LogOutput.of(LogProviderRef.of(u)))
			.build("logging.custom.output");

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(
						PublisherFactory.async().bufferSize(bufferSize.get(config.properties()).value(1024)).build());
				r.appender("console", a -> {
					a.output(output.get(config.properties()).value(LogOutput.ofStandardOut()));
				});
				r.level(Level.INFO);
			})
			.build();

		return Optional.of(gum);
	}

}
// @end
