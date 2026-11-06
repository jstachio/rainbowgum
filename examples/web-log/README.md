# Web log example

A small always-on service, the homelab kind, that shows its recent log on a web page
which follows along as events are written. It uses only the JDK's built-in HTTP server
and Rainbow Gum's existing extension points, no extra dependencies:

- `WebLogOutput` is a `LogOutput` that keeps the last N encoded events in memory, serves
  a page at `/` and streams events to it from `/events` with server-sent events.
- `WebLogConfigurator` registers the `web` output scheme. It is found with the
  `ServiceLoader`, so an appender can use it from `logging.properties` alone.

```properties
logging.appenders=console,web
logging.appender.web.output.uri=web://127.0.0.1:8080/?size=500
logging.appender.web.encoder.uri=ttll
```

The page shows the same text the encoder produces, so any encoder works. The page
colors events by level, filters by text and level, and stops following while you
scroll back.

Build (needs Rainbow Gum installed in the local `.m2` first, `../../mvnw install` from the
repository root) and run, then open http://127.0.0.1:8080/:

```
../../mvnw -q -f pom.xml package
java -jar target/rainbowgum-web-log-example.jar
```

There is no login, and a log can hold anything the application logs. The example only
listens on localhost; use `web://0.0.0.0:8080/` to reach it from other machines on a
firewalled port, or put it behind a reverse proxy with authentication.

A page that cannot keep up misses events rather than slowing the application down,
and a page that reconnects gets the events it has not seen yet.

To copy it into an application, take the two classes, the page and the
`META-INF/services` file. An application that already runs an HTTP server could
instead look the output up by appender name with
`RainbowGum.of().config().outputRegistry().output("web")` and serve the events itself.
