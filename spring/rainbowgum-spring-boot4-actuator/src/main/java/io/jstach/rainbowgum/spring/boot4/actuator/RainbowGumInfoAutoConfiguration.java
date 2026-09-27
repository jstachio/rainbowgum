package io.jstach.rainbowgum.spring.boot4.actuator;

import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorFallback;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers an {@link InfoContributor} that renders each
 * {@link io.jstach.rainbowgum.LogReporter.Section} to {@code /actuator/info}, under a
 * {@code rainbowgum} key.
 * <p>
 * Disabled unless {@code management.info.rainbowgum.enabled=true} is set explicitly
 * ({@link InfoContributorFallback#DISABLE}), the same default Spring Boot's own
 * {@code EnvironmentInfoContributor}/{@code JavaInfoContributor}/{@code OsInfoContributor}/
 * {@code ProcessInfoContributor} use in {@link InfoContributorAutoConfiguration}: this
 * can include internal detail (appender/output/encoder names, URIs) an application may
 * not want exposed on an endpoint that, once enabled, has historically been a source of
 * accidental information disclosure.
 */
@AutoConfiguration(after = InfoContributorAutoConfiguration.class)
public class RainbowGumInfoAutoConfiguration {

	/**
	 * Creates the auto configuration.
	 */
	public RainbowGumInfoAutoConfiguration() {
	}

	/**
	 * Creates the info contributor bean.
	 * @return info contributor.
	 */
	@Bean
	@ConditionalOnEnabledInfoContributor(value = "rainbowgum", fallback = InfoContributorFallback.DISABLE)
	public InfoContributor rainbowGumInfoContributor() {
		return new RainbowGumInfoContributor();
	}

}
