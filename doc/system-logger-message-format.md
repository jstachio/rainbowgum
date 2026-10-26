# `System.Logger` message formatting by implementation

How each `System.LoggerFinder` implementation renders `MessageFormat` style messages from
`System.Logger.log(Level, String, Object...)`, especially apostrophes and a Throwable
passed as a parameter. Measured on 2026-10-22 with JDK 25.

Versions: JDK 25 built-in (`java.util.logging` backend), JBoss LogManager 3.2.2.Final (its own `System.LoggerFinder`, with `-Djava.util.logging.manager=org.jboss.logmanager.LogManager`), Logback 1.5.20 with `slf4j-jdk-platform-logging` 2.0.20, Log4j2 2.25.2 with `log4j-jpl`, tinylog 2.8.1 with `jsl-tinylog`, and Rainbow Gum 0.15.1. `ex` is `new IOException("boom")`. "+ trace" means the stack trace is attached to the event as well.

| Call | JDK (JUL) | JBoss LogManager | Logback | Log4j2 | tinylog | Rainbow Gum 0.15.1 |
|---|---|---|---|---|---|---|
| `log(INFO, "It's done", "x")` | `It's done` | `It's done` | `Its done` | `Its done` | `Its done` | `Its done` |
| `log(INFO, "It's {0}", "x")` | `Its {0}` | `Its {0}` | `Its {0}` | `Its {0}` | `Its {0}` | `Its {0}` |
| `log(INFO, "It's done")` | `It's done` | `It's done` | `It's done` | `It's done` | `It's done` | `It's done` |
| `log(INFO, "failed {0}", new Object[] {ex})` | `failed java.io.IOException: boom` | `failed java.io.IOException: boom` | `failed java.io.IOException: boom` + trace | `failed java.io.IOException: boom` + trace | `failed java.io.IOException: boom` | `failed {0}` + trace |
| `log(INFO, "user {0} failed {1}", "bob", ex)` | `user bob failed java.io.IOException: boom` | `user bob failed java.io.IOException: boom` | `user bob failed java.io.IOException: boom` + trace | `user bob failed java.io.IOException: boom` + trace | `user bob failed java.io.IOException: boom` | `user bob failed {1}` + trace |
| `log(INFO, "user {0} failed", "bob", ex)` | `user bob failed` (exception lost) | `user bob failed` (exception lost) | `user bob failed` + trace | `user bob failed` + trace | `user bob failed` (exception lost) | `user bob failed` + trace |
| `log(INFO, "unmatched {", "x")` | `unmatched {` | event dropped, `ErrorManager` FORMAT_FAILURE | throws `IllegalArgumentException` to caller | event dropped, status logger WARN | `unmatched {` plus internal WARN | `unmatched { [MessageFormat failed: Unmatched braces in the pattern.]` |
| `log(INFO, "value {0}", objWhoseToStringThrows)` | `value {0}` | event dropped, `ErrorManager` FORMAT_FAILURE | throws `RuntimeException` to caller | event dropped, status logger WARN | throws `RuntimeException` to caller | `value {0} [MessageFormat failed: expected]` |

Notes:
- **When the JDK formats:** it only runs `MessageFormat` when there are parameters and the pattern contains `{` followed by a digit. JBoss LogManager does the same. The other implementations run it whenever there are parameters, so in the first row the apostrophe is read as a quote and removed.
- **JBoss LogManager errors:** the JUL `ErrorManager` only reports the first error, so after one formatting failure later bad events are dropped with no message at all.
- **The overload pitfall:** `log(INFO, "failed {0}", ex)` with a single Throwable picks the `log(Level, String, Throwable)` overload. That's why the fourth row passes `new Object[] {ex}`. Through the Throwable overload, every implementation logs `failed {0}` + trace.
- **Rows that agree everywhere:** `"It''s {0}"` gives `It's x` in all six.

## Rainbow Gum 1.0

`logging.messageformat.style` picks which implementation to follow. Measured with the
same program on the 1.0.0 snapshot:

- `jdk` (the default, alias `jboss`): the same output as the JDK column, except that an
  argument whose `toString()` throws logs `value {0} [MessageFormat failed: expected]`.
  `unmatched {` has no argument index, so it is logged as is, as the JDK does.
- `log4j2` (alias `logback`): the same output as the Log4j2 column, except that the two
  failure rows log the raw message followed by `[MessageFormat failed: reason]` instead
  of dropping the event.

Rainbow Gum never drops the event or throws to the caller when formatting fails, in either
style.

## Reproducing

Run this with each implementation's jars on the class path (`java -cp ... SysCases.java`).
JBoss LogManager also needs `-Djava.util.logging.manager=org.jboss.logmanager.LogManager`
and the `io.smallrye.common:smallrye-common-*` jars.

```java
import java.lang.System.Logger.Level;

public class SysCases {

	static final class Bad {

		@Override
		public String toString() {
			throw new RuntimeException("expected");
		}

	}

	public static void main(String[] args) throws Exception {
		var ex = new java.io.IOException("boom");
		var log = System.getLogger("sys");
		run("apostrophe, unused param", () -> log.log(Level.INFO, "It's done", "x"));
		run("apostrophe, used param", () -> log.log(Level.INFO, "It's {0}", "x"));
		run("apostrophe, no params", () -> log.log(Level.INFO, "It's done"));
		run("escaped apostrophe", () -> log.log(Level.INFO, "It''s {0}", "x"));
		run("throwable overload", () -> log.log(Level.INFO, "failed", ex));
		run("throwable only param", () -> log.log(Level.INFO, "failed {0}", new Object[] { ex }));
		run("throwable second param", () -> log.log(Level.INFO, "user {0} failed {1}", "bob", ex));
		run("throwable extra param", () -> log.log(Level.INFO, "user {0} failed", "bob", ex));
		run("malformed pattern", () -> log.log(Level.INFO, "unmatched {", "x"));
		run("toString throws", () -> log.log(Level.INFO, "value {0}", new Bad()));
		Thread.sleep(300);
	}

	static void run(String name, Runnable r) {
		System.out.println("## " + name);
		try {
			r.run();
		}
		catch (Throwable t) {
			System.out.println("THREW " + t);
		}
		System.out.flush();
		System.err.flush();
	}

}
```
