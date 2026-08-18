/**
 * A {@link io.jstach.rainbowgum.LogOutput} backed by <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/module-summary.html">JDK
 * Flight Recorder (JFR)</a>, inspired by
 * <a href="https://github.com/mbien/JFRLog">JFRLog</a>.
 * <p>
 * The Service Loaded configurator adds this output to the output registry with URI scheme
 * {@value io.jstach.rainbowgum.jfr.JfrLogOutput#JFR_SCHEME}, and a near zero-cost encoder
 * under the same scheme (see {@link io.jstach.rainbowgum.jfr.JfrLogOutput} for why one is
 * needed even though the encoded bytes are never used).
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.jfr;
