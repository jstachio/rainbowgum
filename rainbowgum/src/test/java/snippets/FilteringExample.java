package snippets;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogRouter.Router.RouterFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

public class FilteringExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		return builder(config).optional();
	}

	RainbowGum.Builder builder(LogConfig config) {
		return
		// @start region = "filteringExample"
		RainbowGum.builder(config) //
			.route(rb -> {
				rb.factory(RouterFactory.of(e -> {
					/*
					 * We only log DEBUG level events.
					 */
					return switch (e.level()) {
						case DEBUG -> e;
						default -> null;
					};
				}));
				/*
				 * If we do not set the level correctly the router will never get the
				 * event regardless of the logic of the above filtering function.
				 */
				rb.level(Level.DEBUG);
			});
		// @end
	}

}
