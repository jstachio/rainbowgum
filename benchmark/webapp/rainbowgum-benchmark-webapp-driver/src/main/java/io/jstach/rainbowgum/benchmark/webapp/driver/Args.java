package io.jstach.rainbowgum.benchmark.webapp.driver;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Parsed command line flags.
 *
 * <pre>
 * --url &lt;uri&gt;          default http://localhost:8080/api/greet/world
 * --concurrency &lt;n&gt;    default 50
 * --warmup &lt;seconds&gt;   default 0 (no warmup phase)
 * --duration &lt;seconds&gt; default 30
 * --pid &lt;pid&gt;          optional, enables RSS sampling of that process
 * --label &lt;name&gt;       default "run", used as the CSV row label
 * --out &lt;path&gt;         optional CSV file to append the result row to
 * </pre>
 */
record Args(URI url, int concurrency, int warmupSeconds, int durationSeconds, long pid, String label,
		@Nullable Path out) {

	static Args parse(String[] args) {
		Map<String, String> flags = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("--")) {
				String key = arg.substring(2);
				String value = (i + 1 < args.length) ? args[++i] : "true";
				flags.put(key, value);
			}
		}
		String urlStr = flags.getOrDefault("url", "http://localhost:8080/api/greet/world");
		int concurrency = Integer.parseInt(flags.getOrDefault("concurrency", "50"));
		int warmup = Integer.parseInt(flags.getOrDefault("warmup", "0"));
		int duration = Integer.parseInt(flags.getOrDefault("duration", "30"));
		long pid = Long.parseLong(flags.getOrDefault("pid", "0"));
		String label = flags.getOrDefault("label", "run");
		@Nullable
		String outStr = flags.get("out");
		@Nullable
		Path out = outStr == null ? null : Path.of(outStr);
		return new Args(URI.create(urlStr), concurrency, warmup, duration, pid, label, out);
	}

}
