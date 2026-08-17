package snippets;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.pattern.format.PatternRegistry;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.PatternKey;
import io.jstach.rainbowgum.pattern.format.spi.PatternKeywordProvider;

// @start region="customPatternKeyword"
public class CustomPatternKeywordExample extends PatternKeywordProvider {

	@Override
	protected void register(PatternRegistry patternRegistry) {
		// Adds "%hostname" as a usable keyword in patterns.
		patternRegistry.keyword(PatternKey.of("hostname"), (config, node) -> HostnameFormatter.INSTANCE);
	}

	enum HostnameFormatter implements EventFormatter {

		INSTANCE;

		// Resolved once instead of on every event.
		private static final String HOSTNAME = hostname();

		private static String hostname() {
			try {
				return InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException e) {
				return "unknown";
			}
		}

		@Override
		public void format(StringBuilder output, LogEvent event) {
			output.append(HOSTNAME);
		}

	}

}
// @end
