# Web log example

A small always-on service, the homelab kind, that shows its recent log on a web page
which follows along as events are written. It uses only the JDK's built-in HTTP server
and Rainbow Gum's existing extension points, no extra dependencies.

```properties
logging.appenders=console,web
logging.appender.web.output.uri=web://127.0.0.1:8080/
logging.appender.web.encoder.uri=ttll
logging.feed.size=500
```

Build (needs Rainbow Gum installed in the local `.m2` first, `../../mvnw install` from the
repository root) and run, then open http://127.0.0.1:8080/:

```
../../mvnw -q -f pom.xml package
java -jar target/rainbowgum-web-log-example.jar
```

The page shows the same text the encoder produces, so any encoder works. It colors
events by level, filters by text and level, and stops following while you scroll back.

## How it fits together

- `LogFeed` keeps the most recent entries and lets anything follow them.
  `WebLogConfigurator`, found with the `ServiceLoader`, puts one in Rainbow Gum's
  `ServiceRegistry` and registers the `web` output scheme.
- `WebLogOutput` finds the feed when the appender is built, failing if there is none,
  and pushes a copy of each encoded event to it. It also starts `LogFeedServer`, which
  serves the page and streams the feed to it with server-sent events.

Nothing reaches into the output. The output belongs to its appender, which decides when
it is written to, flushed and closed; the feed is the part built to be shared. An
application that already runs an HTTP server can follow the same feed itself and skip
the `web` output's server:

```java
try (var subscription = LogFeed.find(RainbowGum.of().config()).subscribe()) {
	subscription.backlog().forEach(this::send);
	while (!subscription.isClosed()) {
		var entry = subscription.poll(Duration.ofSeconds(15));
		if (entry != null) {
			send(entry);
		}
	}
}
```

## Falling behind

Logging only pushes. An application thread cannot be asked to slow down for a reader
without blocking it, so the feed never waits: each subscription has a bounded queue and
a reader that falls behind misses entries. That is also why the feed is a plain queue
and not a reactive `Flow.Publisher`, whose demand signalling logging cannot honor.

A page that reconnects gets the entries it has not seen yet, and starts over when the
application has restarted.

## Security

There is no login, and a log can hold anything the application logs. The example only
listens on localhost; use `web://0.0.0.0:8080/` to reach it from other machines on a
firewalled port, or put it behind a reverse proxy with authentication.
