# Startup hooks

A placeholder so this is not forgotten before 1.0. Nothing is decided and there is no
code on this branch yet.

The question: Rainbow Gum has shutdown hooks and close callbacks but no general way for
something to run once a Rainbow Gum has started. Some components get a start call, but a
library or framework that is not a component has nothing to hang off. Do we need one for
1.0, and what would actually use it?

## What Rainbow Gum has today

- **Before start:** `RainbowGumServiceProvider.Configurator.configure(config, pass)` runs
  while the config is built, in passes so one configurator can wait for another. JUL,
  Jansi and the pattern module install themselves here.
- **Component start:** `LogLifecycle.start(LogConfig)` on outputs, encoders, appenders and
  publishers, called when the router starts.
- **After a global bind:** `RainbowGum.onGlobalChange(consumer)`, but only for the global
  Rainbow Gum, and a notification is not a guarantee that the gum is still current.
- **Changes:** `LogConfig.changePublisher().subscribe(...)` when change mode is on, and
  `LogRouter.RootRouter.onChange(...)`, which also fires when the global router is
  rebound.
- **Close:** `ServiceRegistry.onClose(AutoCloseable)`, and a configurator that is
  `AutoCloseable` is closed with the registry.
- **Shutdown:** the JVM shutdown hook, with `logging.global.shutdownHook` to turn it off.

The gap is a hook for anyone, global or not, that runs after a Rainbow Gum has started.

## Logback 1.6.6

- **`LoggerContextListener`:** `onStart`, `onReset`, `onStop` and `onLevelChange`, plus
  `isResetResistant()` to survive a reconfiguration. Added in code with
  `LoggerContext.addListener` or in XML with `<contextListener>`.
  `LogbackServiceProvider.initialize()` configures first and then calls
  `LoggerContext.start()`, which fires `onStart`. So a listener registered by the
  configuration does see it.
- **`ConfigurationEventListener`:** `Context.addConfigurationEventListener`. Events:
  `CONFIGURATION_STARTED`, `PARTIAL_CONFIGURATION_ENDED_SUCCESSFULLY`,
  `CONFIGURATION_ENDED_SUCCESSFULLY`, `CONFIGURATION_ENDED_WITH_XML_PARSING_ERRORS`, and
  the `CHANGE_DETECTOR_*` and `CHANGE_DETECTED` events of the scan for file changes.
  In-tree, only tests use it.
- **`Configurator`** (ServiceLoader): configures the context before anything else runs,
  returning `ExecutionStatus` to say whether later configurators should run. This is the
  equivalent of our `Configurator`.
- **`LifeCycle`** (`start`, `stop`, `isStarted`) on every component, started by the
  configuration as each element ends.
- `StatusListener`, `Context.putObject`, and `Context.addScheduledFuture` /
  `register(ExecutorService)` for background work stopped with the context.

In-tree users: `LevelChangePropagator`, the JUL bridge, which only uses `onLevelChange`.

## Log4j2 2.26.1

- **`LoggerContext.addPropertyChangeListener`:** fires `PROPERTY_CONFIG` ("config") with
  the old and new `Configuration` whenever a configuration is set, including the first.
  This is the closest thing to a start hook.
- **`LoggerContextShutdownAware`** (`LoggerContextShutdownEnabled.addShutdownListener`):
  called when a context shuts down. Used by `AbstractLoggerAdapter` and
  `ClassLoaderContextSelector` to drop cached loggers.
- **`ShutdownCallbackRegistry`:** replaces how the shutdown hook is registered
  (`log4j.shutdownCallbackRegistry`), with `log4j2.shutdownHookEnabled` to turn it off.
- **`ConfigurationListener`** (`onChange(Reconfigurable)`): the file monitor's callback.
- **`LifeCycle` / `LifeCycle2`** on every plugin: `initialize`, `start`, `stop(timeout)`.
- `ConfigurationFactory` plugins choose and build the configuration. `LoggerContextAware`
  and `ConfigurationAware` inject the context into components.
- For web applications, `Log4jServletContainerInitializer` and
  `Log4jServletContextListener` tie the context to the servlet context's lifecycle.

In-tree users of the configuration listener: `Log4jBridgeHandler` (JUL, propagating
levels again after a reconfiguration) and `LoggerContextAdmin` (JMX).

## What uses them outside the frameworks

- **Micrometer** (`LogbackMetrics`, `Log4j2Metrics`): counts events with a filter it adds
  itself. On Logback it adds a `LoggerContextListener` and re-adds its `TurboFilter` in
  `onReset`. On Log4j2 it adds a `PropertyChangeListener` and re-adds its filter to every
  logger config when the configuration changes. The hook exists because a reconfiguration
  throws away anything added in code.
- **OpenTelemetry appenders** (`opentelemetry-java-instrumentation`, both frameworks): the
  appender is configured long before the OpenTelemetry SDK exists. It buffers up to
  `numLogsCapturedBeforeOtelInstall` events (default 1000), and the application calls
  `OpenTelemetryAppender.install(openTelemetry)` after startup. That walks every logger's
  appenders looking for its own, then replays the buffer. No framework hook is used.
  This is the "a dependency shows up after logging started" problem, solved by hand.
- **Spring Boot** (`LogbackLoggingSystem`, `Log4J2LoggingSystem`): drives the lifecycle
  itself rather than hooking it. It stores marker objects and the `Environment` in the
  context (`putObject`, `setExternalContext`), adds `LevelChangePropagator`, uses status
  listeners, removes a filter it added during initialization, and stops the context from
  its own shutdown handler.

Revisions surveyed: Logback `v_1.6.6`, Log4j2 `rel/2.26.1`, Spring Boot `8552ac1`
(2026-11-06), opentelemetry-java-instrumentation `1c1ad86` (2026-11-08), Micrometer
`500bba1` (2026-11-07).

## Uses that would apply to Rainbow Gum

- **Late dependencies:** an output or encoder that needs something only available after
  startup, like the OpenTelemetry SDK, a Micrometer registry or a Spring bean. Today the
  answer is the `ServiceRegistry` or a component's own buffer. A hook would let it
  connect once without walking the router.
- **Re-attaching after a rebind:** what Micrometer does on reset, a rebind does to us: a
  new Rainbow Gum has none of what was added in code to the old one.
  `RainbowGum.onGlobalChange` covers the global case.
- **Checking once started:** a startup report, or failing fast when an output cannot
  write, after every component has started rather than inside one of them.
- **Background work tied to the gum:** started after start and stopped on close.

## Open questions

- Does `onGlobalChange` plus `ServiceRegistry.onClose` already cover enough, so this is
  only documentation?
- Is the hook per `RainbowGum` (on the builder or the service registry) or a
  ServiceLoader SPI next to `Configurator`?
- Is it called for every start, including standalone gums that are never bound, and
  again after a rebind?
- Which thread runs it, under which lock, and what happens when it throws: fail the
  start, or alert?
- How does it fit the lifecycle spec from the review triage (findings 3, 12 and 17)? It
  probably belongs in that spec rather than on its own.
