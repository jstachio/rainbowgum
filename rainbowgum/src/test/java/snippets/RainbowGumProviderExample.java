package snippets;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

// @start region="provider"
class RainbowGumProviderExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		Property<Integer> bufferSize = Property.builder() //
			.ofInt()
			.orElse(1024)
			.build("logging.custom.async.bufferSize");

		LogProvider<LogOutput> output = Property.builder()
			.ofProvider(LogOutput::of)
			.orElse(LogOutput.ofStandardOut())
			.withKey("logging.custom.output")
			.provider(o -> o);

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(PublisherFactory //
					.async() //
					.bufferSize(r.value(bufferSize)) //
					.build());
				r.appender("console", a -> {
					a.output(output);
				});
				r.level(Level.INFO);
			})
			.build();

		return Optional.of(gum);
	}

}
// @end
