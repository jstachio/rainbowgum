/**
 * <strong>EXPERIMENTAL</strong> encoder that formats into a reused {@link StringBuilder}
 * like the default formatter path but then encodes directly into a reused
 * {@link java.nio.ByteBuffer} via a {@link java.nio.charset.CharsetEncoder}, skipping the
 * intermediate {@code String}/{@code byte[]} allocation that
 * {@code LogOutput.write(LogEvent, String)}'s default otherwise does on every call. This
 * is the same technique Log4j2's garbage-free encoders use.
 */
module io.jstach.rainbowgum.nio {

	exports io.jstach.rainbowgum.nio;

	requires transitive io.jstach.rainbowgum;
	requires static org.eclipse.jdt.annotation;

}
