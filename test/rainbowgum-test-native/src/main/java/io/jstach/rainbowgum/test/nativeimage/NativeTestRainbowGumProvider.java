package io.jstach.rainbowgum.test.nativeimage;

import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;
import io.jstach.svc.ServiceProvider;

/*
 * ServiceLoader-discovered (see the generated META-INF/services/io.jstach.rainbowgum.spi
 * .RainbowGumServiceProvider file, produced by pistachio-svc-apt from the annotation
 * below), not instantiated directly by Main. That is the actual point of this class:
 * proving a user-supplied ServiceLoader provider, not just Rainbow Gum's own built-in
 * ones, is still resolvable and correctly selected under GraalVM native-image's
 * closed-world analysis.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class NativeTestRainbowGumProvider implements RainbowGumProvider {

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public NativeTestRainbowGumProvider() {
	}

	@Override
	public int priority() {
		// Higher than the default (0) every other provider on this module's classpath
		// uses, so this test provider always wins selection over rainbowgum-simple's
		// own zero-config bootstrap.
		return 100;
	}

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("nativetest", a -> {
				a.output(LogOutput.ofStandardOut());
				a.formatter(LogFormatter.builder().level().space().loggerName().space().message().newline().build());
			});
		}).build();
		return Optional.of(gum);
	}

}
