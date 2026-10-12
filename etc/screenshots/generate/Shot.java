import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Logs a fixed set of sample events through a real Rainbow Gum configured only by the
 * properties file given as the first argument. Output goes to stdout as the encoder writes
 * it, ANSI codes included.
 */
public class Shot {

	public static void main(String[] args) throws Exception {
		String text = Files.readString(Path.of(args[0]));
		var props = LogProperties.builder().fromProperties(text).build();
		var config = LogConfig.builder().properties(props).build();
		int max = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;
		try (var gum = RainbowGum.builder(config).build().start()) {
			var events = new java.util.ArrayList<LogEvent>();
			var t = Instant.parse("2026-10-12T14:03:27.481Z");
			events.add(event(t, "main", Level.INFO, "com.example.orders.OrdersApplication",
					"Started OrdersApplication in 0.412 seconds", kv()));
			events.add(event(t.plusMillis(1180), "http-exec-1", Level.DEBUG, "com.example.orders.web.OrderController",
					"GET /orders/1042", kv("requestId", "7f3a9c", "user", "ada")));
			events.add(event(t.plusMillis(1193), "http-exec-1", Level.INFO, "com.example.orders.OrderService",
					"Order 1042 shipped", kv("requestId", "7f3a9c", "user", "ada")));
			events.add(event(t.plusMillis(2307), "http-exec-3", Level.WARNING, "com.example.orders.InventoryClient",
					"Inventory slow, retrying", kv("requestId", "b81e02", "attempt", "2")));
			events.add(event(t.plusMillis(2950), "http-exec-3", Level.ERROR, "com.example.orders.InventoryClient",
					"Inventory unavailable", kv("requestId", "b81e02", "attempt", "3")));
			events.add(event(t.plusMillis(3012), "scheduler-1", Level.TRACE, "com.example.orders.cache.OrderCache",
					"Evicted 12 entries", kv()));
			events.stream().limit(max).forEach(gum::log);
		}
	}

	static LogEvent event(Instant t, String thread, Level level, String logger, String msg, KeyValues kvs) {
		return LogEvent.of(t, thread, 1, level, logger, msg, kvs, null);
	}

	static KeyValues kv(String... pairs) {
		var m = new LinkedHashMap<String, String>();
		for (int i = 0; i < pairs.length; i += 2) {
			m.put(pairs[i], pairs[i + 1]);
		}
		return KeyValues.of(m);
	}

}
