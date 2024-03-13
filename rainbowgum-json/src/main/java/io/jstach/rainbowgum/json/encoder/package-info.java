/**
 * Common JSON encoders like GELF.
 * <p>
 * The Service Loaded configurator adds
 * <a href="https://go2docs.graylog.org/5-2/getting_in_log_data/gelf.html">GELF JSON</a>
 * Encoder to encoder registry with {@value GelfEncoder#GELF_SCHEME} URI scheme.
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.json.encoder;