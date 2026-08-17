package snippets;

import java.time.ZoneOffset;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.pattern.format.PatternConfig;

public class PatternConfigExample {

	// @start region = "patternConfigExample"
	LogConfig.Builder configure(LogConfig.Builder builder) {
		return builder.configurator(PatternConfig.builder() //
			.zoneId(ZoneOffset.UTC) //
			.ansiDisabled(true) //
			.build());
	}
	// @end

}
