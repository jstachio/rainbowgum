package io.jstach.rainbowgum.test.jbosslogging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * JBoss Logging has no native rainbowgum-jboss-logging module (see todo.md) - it only
 * reaches RainbowGum through its own SLF4J provider (org.jboss.logging.Slf4jLogger /
 * Slf4jLocationAwareLogger, bundled in the jboss-logging jar itself). Unlike
 * jcl-over-slf4j, this is NOT auto-selected just because slf4j-api is on the classpath -
 * confirmed by decompiling org.jboss.logging.LoggerProviders: without the
 * org.jboss.logging.provider=slf4j system property (set in this module's pom.xml for
 * surefire), it silently falls back to java.util.logging and never reaches RainbowGum at
 * all, still reporting isInfoEnabled()=true the whole time. Once that property is set,
 * Slf4jLocationAwareLogger passes its own fqcn (org.jboss.logging.Logger) through to
 * SLF4J's LocationAwareLogger, a different fqcn boundary than jcl-over-slf4j's - this
 * proves LocationAwareForwardingLogger.findCaller handles that boundary correctly too,
 * not just the FakeBridge stand-in rainbowgum-slf4j's own tests use.
 */
class JBossLoggingCallerInfoTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testCallerInfoSurvivesTheJbossLoggingSlf4jBridge() {
		String name = "jboss.caller";
		String properties = """
				logging.global.change=true
				logging.caller.%s=true
				logging.level.%s=INFO
				""".formatted(name, name);
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					output.append(event.level()).append(" ");
					event.formattedMessage(output);
					Caller caller = event.callerOrNull();
					if (caller != null) {
						output.append(" <caller>");
						output.append(caller.className());
						output.append(".");
						output.append(caller.methodName());
						output.append(":");
						output.append(caller.lineNumber());
						output.append("</caller>");
					}
					output.append("\n");
				});
				a.output(list);
			});
		}).set();

		try {
			Logger log = Logger.getLogger(name);
			log.info("hello");

			String expected = "INFO hello <caller>io.jstach.rainbowgum.test.jbosslogging.JBossLoggingCallerInfoTest"
					+ ".testCallerInfoSurvivesTheJbossLoggingSlf4jBridge:64</caller>\n";
			assertEquals(expected, list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

}
