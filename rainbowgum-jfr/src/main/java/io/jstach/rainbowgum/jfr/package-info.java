/**
 * A {@link io.jstach.rainbowgum.LogOutput} backed by <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/module-summary.html">JDK
 * Flight Recorder (JFR)</a>, inspired by
 * <a href="https://github.com/mbien/JFRLog">JFRLog</a>.
 * <p>
 * The Service Loaded configurator adds this output to the output registry with URI scheme
 * {@value io.jstach.rainbowgum.jfr.JfrLogOutput#JFR_SCHEME}.
 */
@org.jspecify.annotations.NullMarked
package io.jstach.rainbowgum.jfr;
