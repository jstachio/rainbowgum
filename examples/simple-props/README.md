# Simple props example

A plain Java application logging through SLF4J, configured only by
`src/main/resources/logging.properties`. This is the setup for an application with no
framework: `rainbowgum-slf4j` for SLF4J and `rainbowgum-simple-props` to load the
properties file from the classpath.

Build (needs Rainbow Gum installed in the local `.m2` first, `../../mvnw install` from the
repository root) and run:

```
../../mvnw -q -f pom.xml package
java -jar target/rainbowgum-simple-props-example.jar
```

```
13:26:30.070 [main] [INFO] Main - Hello from Rainbow Gum
13:26:30.072 [main] [DEBUG] Main - Debug is enabled for this package in logging.properties
13:26:30.073 [main] [INFO] Main {user=ada&requestId=42} - Request handled
13:26:30.073 [main] [ERROR] Main - Something failed
java.lang.IllegalStateException: boom
	at rainbowgum.simpleprops.example.Main.main(Main.java:27)
```

Select the `json` profile, which loads `logging-json.properties` on top of
`logging.properties`, to switch the encoder to JSON5:

```
java -Dlogging.profiles=json -jar target/rainbowgum-simple-props-example.jar
```

Any key can also be set with a system property (`-Dlogging.level=DEBUG`) or an
environment variable (`RAINBOWGUM_level=DEBUG`), which take precedence over the files.

The jar's manifest `Class-Path` points straight at the jars in the local Maven
repository, so there is no copy or fat jar step. That makes for the fastest build and
startup, but the jar only runs on the machine that built it.
