package io.jstach.rainbowgum.test.jdk;

import java.time.Instant;
import java.util.ServiceLoader;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogRouter.Router.RouterFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public class JDKSetup {

	public static RainbowGum run(ListLogOutput output, System.Logger.Level level) {
		var config = LogConfig.builder() //
			.serviceLoader(ServiceLoader.load(RainbowGumServiceProvider.class)) //
			.level(level) //
			.build();
		RainbowGum.set(() -> RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
			});
			r.factory(RouterFactory.of(e -> e.freeze(Instant.EPOCH)));
		}).build());
		return RainbowGum.of();
	}

}
