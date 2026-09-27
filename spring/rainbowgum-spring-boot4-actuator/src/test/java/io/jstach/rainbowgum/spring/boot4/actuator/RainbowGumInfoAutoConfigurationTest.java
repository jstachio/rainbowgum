package io.jstach.rainbowgum.spring.boot4.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Boots a real (if minimal) Spring context, registering the same
 * InfoContributorAutoConfiguration a real Spring Boot app with spring-boot-starter-
 * actuator on the classpath would run, not just this module's own
 * RainbowGumInfoAutoConfiguration, so the @ConditionalOnEnabledInfoContributor gating
 * runs for real rather than assuming it would. Also exercises the JPMS module boundary
 * (CGLIB enhancement of the auto configuration class, see module-info's `opens`).
 * <p>
 * Touches RainbowGum's static current-instance holder: see RainbowGumTest's identical
 * note (in rainbowgum-core) for why @Isolated/@Execution(SAME_THREAD) are needed.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RainbowGumInfoAutoConfigurationTest {

	@Test
	void disabledByDefault() {
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).set()) {
			try (var context = new AnnotationConfigApplicationContext()) {
				context.register(InfoContributorAutoConfiguration.class, RainbowGumInfoAutoConfiguration.class);
				context.refresh();

				assertTrue(context.getBeansOfType(InfoContributor.class).isEmpty(),
						"management.info.rainbowgum.enabled defaults to false, matching Spring Boot's own "
								+ "env/java/os/process contributors");
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void oneMapEntryPerSectionWhenEnabled() {
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).set()) {
			try (var context = new AnnotationConfigApplicationContext()) {
				context.getEnvironment()
					.getPropertySources()
					.addFirst(new MapPropertySource("test", Map.of("management.info.rainbowgum.enabled", "true")));
				context.register(InfoContributorAutoConfiguration.class, RainbowGumInfoAutoConfiguration.class);
				context.refresh();

				var contributor = context.getBean(InfoContributor.class);
				var builder = new Info.Builder();
				contributor.contribute(builder);
				var info = builder.build();

				var rainbowgum = (Map<String, String>) info.get(RainbowGumInfoContributor.DETAIL_KEY);
				assertEquals(Set.of("version", "components", "metrics", "alerts", "loggers", "facades"),
						rainbowgum.keySet());
				assertTrue(rainbowgum.get("version").startsWith("Rainbow Gum "));
				assertTrue(rainbowgum.get("components").contains("Global Properties:"));
				assertTrue(rainbowgum.get("metrics").startsWith("Metrics:"));
				assertTrue(rainbowgum.get("alerts").startsWith("Alerts (total=0"));
				assertTrue(rainbowgum.get("loggers").startsWith("Loggers:"));
				assertTrue(rainbowgum.get("facades").startsWith("Facades:"));
			}
		}
	}

	@Test
	void noRainbowGumBoundYet() {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment()
				.getPropertySources()
				.addFirst(new MapPropertySource("test", Map.of("management.info.rainbowgum.enabled", "true")));
			context.register(InfoContributorAutoConfiguration.class, RainbowGumInfoAutoConfiguration.class);
			context.refresh();

			var contributor = context.getBean(InfoContributor.class);
			var builder = new Info.Builder();
			contributor.contribute(builder);

			assertFalse(builder.build().getDetails().containsKey(RainbowGumInfoContributor.DETAIL_KEY),
					"no RainbowGum bound at all should contribute nothing, not throw or contribute an empty map");
		}
	}

}
