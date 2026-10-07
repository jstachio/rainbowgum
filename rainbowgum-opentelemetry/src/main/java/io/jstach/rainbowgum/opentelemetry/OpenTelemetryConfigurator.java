package io.jstach.rainbowgum.opentelemetry;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers the current OpenTelemetry span's ids as the
 * {@link KeyValuesContributor.Source.Standard#OPENTELEMETRY} key values source. Simply
 * having this module on the classpath is enough; disable it with
 * <code>logging.keyvalues.disabled=opentelemetry</code>.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class OpenTelemetryConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * Key value holding the current span's trace id.
	 */
	public static final String TRACE_ID_KEY = "traceId";

	/**
	 * Key value holding the current span's span id.
	 */
	public static final String SPAN_ID_KEY = "spanId";

	/**
	 * For service loader.
	 */
	public OpenTelemetryConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.OPENTELEMETRY,
				OpenTelemetryContributor.INSTANCE);
		return true;
	}

}
