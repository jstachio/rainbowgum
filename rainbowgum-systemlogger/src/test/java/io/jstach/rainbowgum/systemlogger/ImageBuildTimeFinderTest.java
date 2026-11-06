package io.jstach.rainbowgum.systemlogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder.InitOption;

@Isolated
class ImageBuildTimeFinderTest {

	private static final String IMAGE_CODE = "org.graalvm.nativeimage.imagecode";

	/*
	 * GraalVM's TrustStoreManagerFeature asks System.getLogger("jdk.event.security")
	 * while the image is built. Starting a gum then put it, and the JUL handler it
	 * installs, into the image heap and failed the build when no SLF4J was on the class
	 * path.
	 */
	@Test
	void imageBuildDoesNotStartRainbowGumAndRunTimeStillDoes() {
		var finder = new RainbowGumSystemLoggerFinder(() -> InitOption.TRUE) {
		};
		var module = getClass().getModule();
		String previous = System.setProperty(IMAGE_CODE, "buildtime");
		System.Logger buildTimeLogger;
		try {
			buildTimeLogger = finder.getLogger("jdk.event.security", module);
			buildTimeLogger.isLoggable(System.Logger.Level.INFO);
			assertNull(RainbowGum.getOrNull(), "an image build must not start Rainbow Gum");
		}
		finally {
			restore(previous);
		}
		var output = new ListLogOutput();
		var config = LogConfig.builder().build();
		/*
		 * The same finder instance is what the image heap holds at run time. Its first
		 * real lookup must apply the init option; TRUE reuses this bound gum.
		 */
		try (var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).set()) {
			finder.getLogger("runtime", module).log(System.Logger.Level.INFO, "at run time");
			buildTimeLogger.log(System.Logger.Level.INFO, "from the build time logger");
			assertEquals(2, output.events().size());
		}
	}

	private static void restore(@Nullable String previous) {
		if (previous == null) {
			System.getProperties().remove(IMAGE_CODE);
		}
		else {
			System.setProperty(IMAGE_CODE, previous);
		}
	}

}
