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
			.build("custom.async.bufferSize");

		Property<LogOutput> output = Property.builder()
			.map(URI::create)
			.map(config::output)
			.orElse(LogOutput.ofStandardOut())
			.build("custom.output");

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(PublisherProvider.async().bufferSize(config.get(bufferSize).value()).build());
				r.appender(a -> {
					a.output(config.get(output).value());
				});
				r.level(Level.INFO);
			})
			.build();

		return Optional.of(gum);
	}

}
// @end
