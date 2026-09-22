package io.jstach.rainbowgum.test.jcl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * jcl-over-slf4j is Apache Commons Logging's own SLF4J bridge - there is no native
 * rainbowgum-jcl module (see todo.md), so this is the only supported path for JCL
 * users. jcl-over-slf4j's SLF4JLocationAwareLog inserts its own frame between the real
 * caller and rainbowgum-slf4j's LocationAwareLogger.log(...) call, so this proves the
 * fqcn-based frame skip in LocationAwareForwardingLogger.findCaller actually survives a
 * real bridge, not just the FakeBridge stand-in rainbowgum-slf4j's own tests use.
 */
class JclCallerInfoTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testCallerInfoSurvivesTheJclOverSlf4jBridge() {
		String name = "jcl.caller";
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
			Log log = LogFactory.getLog(name);
			log.info("hello");

			String expected = "INFO hello <caller>io.jstach.rainbowgum.test.jcl.JclCallerInfoTest"
					+ ".testCallerInfoSurvivesTheJclOverSlf4jBridge:59</caller>\n";
			assertEquals(expected, list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

}
