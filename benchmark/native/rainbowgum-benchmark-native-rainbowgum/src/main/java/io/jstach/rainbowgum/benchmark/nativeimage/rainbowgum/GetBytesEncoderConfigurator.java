package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Registers the {@value #SCHEME} URI scheme so
 * {@code logging.appender.console.encoder=get-bytes-ttll:///} resolves to the standard
 * TTLL encoder built with {@code LogEncoder.Builder#useGetBytes(true)} - pairs
 * {@code String.getBytes(UTF_8)}-based encoding with the default (BYTES-hinting) stdout
 * output, unlike {@link StringStdOutOutput} (which changes the output's write-method hint
 * instead of the encoder's byte-conversion strategy). Registered via a hand-written
 * {@code META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider} entry,
 * same as {@link StringStdOutConfigurator}/{@link BufferedStdOutConfigurator}.
 * <p>
 * {@code useGetBytes(true)} only exists on {@code feature/log-encoder-copy-chars}, not
 * this branch's own core - this module is compiled/run against a core jar installed
 * locally from that branch (same {@code 0.12.0-SNAPSHOT} coordinate), not a source
 * dependency of this branch.
 */
public final class GetBytesEncoderConfigurator implements RainbowGumServiceProvider.Configurator {

	static final String SCHEME = "get-bytes-ttll";

	/**
	 * No arg for service loader.
	 */
	public GetBytesEncoderConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(SCHEME, ref -> LogEncoder.builder().useGetBytes(true).build());
		return true;
	}

}
