package io.jstach.rainbowgum.slf4j;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import io.jstach.rainbowgum.RainbowGum;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(SLF4JServiceProvider.class)
public class RainbowGumSLF4JServiceProvider implements SLF4JServiceProvider {

	/**
	 * Declare the version of the SLF4J API this implementation is compiled against. The
	 * value of this field is modified with each major release.
	 */
	private static final String REQUESTED_API_VERSION = "2.0";

	@Nullable
	private ILoggerFactory loggerFactory;

	@Nullable
	private IMarkerFactory markerFactory;

	@Nullable
	private MDCAdapter mdcAdapter;

	@Override
	public ILoggerFactory getLoggerFactory() {
		return require(loggerFactory);
	}

	@Override
	public IMarkerFactory getMarkerFactory() {
		return require(markerFactory);
	}

	@Override
	public MDCAdapter getMDCAdapter() {
		return require(mdcAdapter);
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
		loggerFactory = new RainbowGumLoggerFactory(rainbowGum);
		markerFactory = new BasicMarkerFactory();
		mdcAdapter = new RainbowGumMDCAdapter();
		System.setProperty("SLF4J_LOGGING_LOADED", "true");
	}

}
