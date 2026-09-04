package io.jstach.rainbowgum.benchmark.webapp.rainbowgum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.RainbowGum;

/**
 * Plain-text dump of what's actually wired up at runtime: every registered
 * {@link LogAppender} (by {@code toString()}, which includes its concrete class, encoder,
 * output, and flags) plus the concrete SLF4J {@link Logger} implementation this app's own
 * logger is bound to. Exists so a benchmark result can be paired with a confirmed reading
 * of the configuration that produced it, rather than an assumption about which
 * appender/flag combination the JDK-version-sniffed default picked.
 */
@RestController
public class ConfigReportController {

	private static final Logger log = LoggerFactory.getLogger(ConfigReportController.class);

	/**
	 * For Spring.
	 */
	public ConfigReportController() {
	}

	@GetMapping(value = "/api/config-report", produces = "text/plain")
	public String report() {
		var gum = RainbowGum.of();
		var registry = gum.config().serviceRegistry();
		var sb = new StringBuilder();
		sb.append("slf4j.logger=").append(log).append(System.lineSeparator());
		for (var appender : registry.find(LogAppender.class)) {
			sb.append("appender=").append(appender).append(System.lineSeparator());
		}
		return sb.toString();
	}

}
