package snippets;

import java.util.EnumSet;

import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.LogReporter.Section;
import io.jstach.rainbowgum.RainbowGum;

public class LogReporterExample {

	// @start region = "reportingExample"
	/*
	 * A full diagnostic report of what actually got wired up: version, the component tree
	 * (routers, publishers, appenders, encoders, outputs), metrics, and recent alerts.
	 * Cheap to build on demand - print it on an unhandled exception, expose it from an
	 * admin endpoint, or just log it once at startup.
	 */
	public String diagnosticReport(RainbowGum gum) {
		var reporter = LogReporter.builder()
			.sections(EnumSet.of(Section.VERSION, Section.COMPONENTS, Section.METRICS, Section.ALERTS))
			.build();
		return reporter.report(gum);
	}
	// @end

}
