package io.jstach.rainbowgum.spring.boot4.actuator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.LogReporter.Section;
import io.jstach.rainbowgum.RainbowGum;

/**
 * Contributes one {@link LogReporter} rendering per {@link Section} to
 * {@code /actuator/info}, nested under a single {@value #DETAIL_KEY} key so it does not
 * collide with any other contributor's own top level keys.
 * <p>
 * Each section is rendered as its own single-section report from the bound config's
 * {@link LogReporter}.
 */
final class RainbowGumInfoContributor implements InfoContributor {

	/**
	 * The single top level {@link Info.Builder#withDetail(String, Object)} key this
	 * contributor writes to; its value is a map with one entry per {@link Section}.
	 */
	static final String DETAIL_KEY = "rainbowgum";

	RainbowGumInfoContributor() {
	}

	@Override
	public void contribute(Info.Builder builder) {
		var gum = RainbowGum.getOrNull();
		if (gum == null) {
			return;
		}
		Map<String, String> sections = new LinkedHashMap<>();
		for (Section section : Section.values()) {
			sections.put(section.name().toLowerCase(Locale.ROOT),
					gum.config().reporter().report(LogReporter.Options.of(section)));
		}
		builder.withDetail(DETAIL_KEY, sections);
	}

}
