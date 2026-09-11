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
	 * Whether MDC is available at all - {@code ENABLED} (the default) is today's existing
	 * {@link ArrayMDCAdapter} behavior; {@code DISABLED} swaps in a
	 * {@link RainbowGumMDCAdapter#RainbowGumMDCAdapter(boolean) disabled} instance
	 * instead, whose every method is a no-op/empty-returning stub that never touches
	 * either of {@link ArrayMDCAdapter}'s {@link ThreadLocal} fields - for deployments
	 * that want a hard guarantee of no {@link ThreadLocal} anywhere in the logging path
	 * and are fine losing MDC entirely to get it.
	 *
	 * @apiNote whether a disabled MDC should alert/warn on use (someone called
	 * {@code MDC.put(...)} expecting it to work) instead of silently doing nothing is
	 * still an open question - not implemented either way yet, silently doing nothing is
	 * simplest starting point.
	 */
	enum MDCSetting {

		/**
		 * MDC works normally - {@link ArrayMDCAdapter}'s existing
		 * {@link ThreadLocal}-backed behavior, unchanged.
		 */
		ENABLED,
		/**
		 * MDC is completely turned off - every {@link MDCAdapter} method becomes a
		 * no-op/empty-returning stub, and neither of {@link ArrayMDCAdapter}'s
		 * {@link ThreadLocal} fields is ever touched.
		 */
		DISABLED;

		static MDCSetting parse(String value) {
			return MDCSetting.valueOf(value.toUpperCase(Locale.ROOT));
		}

	}

	/**
	 * {@code logging.mdc} - {@code ENABLED} (default) or {@code DISABLED}. Read once,
	 * during {@link #initialize(RainbowGum)}, since
	 * {@link #RainbowGumSLF4JServiceProvider()} (called by
	 * {@link java.util.ServiceLoader}) runs before any {@link RainbowGum} (and therefore
	 * any properties) exist yet.
	 */
	static final String LOGGING_MDC_PROPERTY = "logging.mdc";

	@Nullable
	private ILoggerFactory loggerFactory;

	private final IMarkerFactory markerFactory;

	/*
	 * Not final: initialize(RainbowGum) may swap this from the default ENABLED instance
	 * constructed below to a DISABLED one once logging.mdc can actually be read - see
	 * that method. Any MDCAdapter method called between construction and initialize()
	 * running (an SLF4J-bootstrap-ordering edge case, not expected in normal use) still
	 * observes the default ENABLED/ThreadLocal-backed instance either way.
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
		var setting = rainbowGum.config()
			.properties()
			.forKey(LOGGING_MDC_PROPERTY)
			.ofString()
			.map(MDCSetting::parse)
			.or(MDCSetting.ENABLED)
			.value();
		if (setting == MDCSetting.DISABLED) {
			mdcAdapter = new RainbowGumMDCAdapter(true);
		}
		loggerFactory = new RainbowGumLoggerFactory(rainbowGum, mdcAdapter);
	}

}
