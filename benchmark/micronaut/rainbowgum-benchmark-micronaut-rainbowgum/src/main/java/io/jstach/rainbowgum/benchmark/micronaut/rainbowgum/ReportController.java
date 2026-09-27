package io.jstach.rainbowgum.benchmark.micronaut.rainbowgum;

import java.util.EnumSet;

import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.LogReporter.Section;
import io.jstach.rainbowgum.RainbowGum;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

/**
 * Plain text dump of what actually got wired up at runtime, via the real
 * {@link LogReporter} rather than a one-off hand-rolled dump: which concrete appender,
 * encoder, and output the "console" route resolved to. Exists so a benchmark result can
 * be paired with a confirmed reading of the configuration that produced it, rather than
 * an assumption about which encoder a properties file resolved to.
 */
@Controller("/api")
public class ReportController {

	private static final LogReporter REPORTER = LogReporter.builder()
		.sections(EnumSet.of(Section.VERSION, Section.COMPONENTS))
		.build();

	/**
	 * For Micronaut.
	 */
	public ReportController() {
	}

	@Get("/config-report")
	@Produces(MediaType.TEXT_PLAIN)
	public String report() {
		return REPORTER.report(RainbowGum.of());
	}

}
