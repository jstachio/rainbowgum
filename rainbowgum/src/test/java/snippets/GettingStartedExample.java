package snippets;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

// @start region="gettingStarted"
public class GettingStartedExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		return RainbowGum.builder(config) //
			.route(r -> {
				r.level(Level.DEBUG, "com.myapp");
				r.appender("console", a -> {
					a.encoder(new PatternEncoderBuilder("console")
						// We use the pattern encoder which follows logback pattern
						// syntax.
						.pattern("[%thread] %-5level %logger{15} - %msg%n")
						// We use properties to override the above pattern if set.
						.fromProperties(config.properties())
						.build());
				});
			}) //
			.optional();
	}

}
// @end
