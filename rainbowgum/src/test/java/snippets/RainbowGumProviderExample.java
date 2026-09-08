package snippets;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

// @start region="provider"
class RainbowGumProviderExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		Integer bufferSize = config.properties() //
			.forKey("logging.custom.async.bufferSize")
			.ofInt()
			.or(1024)
			.value();

		LogProvider<LogOutput> output = (name, cfg) -> cfg.properties()
			.forKey("logging.custom.output")
			.ofProvider(LogOutput::of)
			.or(LogOutput.ofStandardOut())
			.value()
			.provide(name, cfg);

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(PublisherFactory //
					.async() //
					.bufferSize(bufferSize) //
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
