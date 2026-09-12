package io.jstach.rainbowgum.slf4j;

import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import io.jstach.rainbowgum.RainbowGum;
import io.jstach.svc.ServiceProvider;

/**
 * SLF4J provider using RainbowGum.
 */
@ServiceProvider(SLF4JServiceProvider.class)
public class RainbowGumSLF4JServiceProvider implements SLF4JServiceProvider {

	/**
	 * Declare the version of the SLF4J API this implementation is compiled against. The
	 * value of this field is modified with each major release.
	 */
	private static final String REQUESTED_API_VERSION = "2.0";

	/**
	 * Which {@link RainbowGumMDCAdapter} implementation to use - {@code THREAD_LOCAL}
	 * (the default) is today's existing {@link ArrayMDCAdapter} behavior; {@code NOOP}
	 * swaps in a {@link NoopMDCAdapter} instead, whose every method is a
	 * no-op/empty-returning stub that never touches either of {@link ArrayMDCAdapter}'s
	 * {@link ThreadLocal} fields - for deployments that want a hard guarantee of no
	 * {@link ThreadLocal} anywhere in the logging path and are fine losing MDC entirely
	 * to get it. Kept as a type rather than an enabled/disabled toggle so a future third
	 * implementation - e.g. one backed by {@code ScopedValue} instead of
	 * {@link ThreadLocal}, once that's a viable MDC storage strategy - can be added
	 * without a breaking property-format change.
	 */
	enum MDCType {

		/**
		 * MDC works normally - {@link ArrayMDCAdapter}'s existing
		 * {@link ThreadLocal}-backed behavior, unchanged.
		 */
		THREAD_LOCAL,
		/**
		 * MDC is completely turned off - {@link NoopMDCAdapter} is used instead of
		 * {@link ArrayMDCAdapter}.
		 */
		NOOP;

		static MDCType parse(String value) {
			return MDCType.valueOf(value.toUpperCase(Locale.ROOT));
		}

	}

	/**
	 * {@code logging.mdc.type} - {@code THREAD_LOCAL} (default) or {@code NOOP}. Read
	 * once, during {@link #initialize(RainbowGum)}, since
	 * {@link #RainbowGumSLF4JServiceProvider()} (called by
	 * {@link java.util.ServiceLoader}) runs before any {@link RainbowGum} (and therefore
	 * any properties) exist yet.
	 */
	static final String LOGGING_MDC_TYPE_PROPERTY = "logging.mdc.type";

	@Nullable
	private ILoggerFactory loggerFactory;

	private final IMarkerFactory markerFactory;

	/*
	 * Not final: initialize(RainbowGum) may swap this from the default THREAD_LOCAL
	 * instance constructed below to a NOOP one once logging.mdc.type can actually be read
	 * - see that method. Any MDCAdapter method called between construction and
	 * initialize() running (an SLF4J-bootstrap-ordering edge case, not expected in normal
	 * use) still observes the default THREAD_LOCAL instance either way.
	 */
	private RainbowGumMDCAdapter mdcAdapter;

	/**
	 * No Arg for service laoder.
	 */
	public RainbowGumSLF4JServiceProvider() {
		mdcAdapter = new RainbowGumMDCAdapter();
		markerFactory = new BasicMarkerFactory();
	}

	@Override
	public ILoggerFactory getLoggerFactory() {
		return require(loggerFactory);
	}

	@Override
	public IMarkerFactory getMarkerFactory() {
		return markerFactory;
	}

	@Override
	public MDCAdapter getMDCAdapter() {
		return mdcAdapter;
	}

	@Override
	public String getRequestedApiVersion() {
		return REQUESTED_API_VERSION;
	}

	private static <T> T require(@Nullable T factory) {
		if (factory == null) {
			throw new IllegalStateException("slf4j was not initialized correctly");
		}
		return factory;
	}

	@Override
	public void initialize() {
		/*
		 * Make JBoss logging use us
		 */
		if (System.getProperty("org.jboss.logging.provider") == null) {
			System.setProperty("org.jboss.logging.provider", "slf4j");
		}
		RainbowGum rainbowGum = RainbowGum.of();
		initialize(rainbowGum);
		System.setProperty("SLF4J_LOGGING_LOADED", "true");
	}

	/**
	 * For testing Rainbow Gums SLF4J without initializing SLF4J.
	 * @param rainbowGum which gum to use for logger factory.
	 */
	public void initialize(RainbowGum rainbowGum) {
		var type = rainbowGum.config()
			.properties()
			.forKey(LOGGING_MDC_TYPE_PROPERTY)
			.ofString()
			.map(MDCType::parse)
			.or(MDCType.THREAD_LOCAL)
			.value();
		if (type == MDCType.NOOP) {
			mdcAdapter = new NoopMDCAdapter();
		}
		loggerFactory = new RainbowGumLoggerFactory(rainbowGum, mdcAdapter);
	}

}
