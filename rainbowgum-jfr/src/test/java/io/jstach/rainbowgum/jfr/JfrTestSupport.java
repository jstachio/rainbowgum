package io.jstach.rainbowgum.jfr;

/**
 * Test-only workaround for a JDK 26 (Temurin 26.0.2+10, reproduced on this environment)
 * JFR quirk: the <em>first</em> {@code Event.commit()} ever executed for one of
 * {@link RainbowGumLogEvent}'s (or {@link RainbowGumAlertEvent}'s) sealed permitted
 * subclasses in a fresh JVM throws {@link LinkageError} ("attempted duplicate class
 * definition") <strong>if</strong> that first commit happens while a
 * {@code jdk.jfr.Recording} that {@code enable(...)}s the class is active. A bare
 * {@code commit()} with no active recording does not trigger it. Reproduced with plain
 * JFR API calls only - no Rainbow Gum code involved - and independent of whether the base
 * class is {@code sealed}, and of whether the class has ever been shared across multiple
 * callers (see {@code doc/jdk-jfr-bugs.md} for the full writeup, including that finding).
 * Only ever hit when a single test method/class is the first in the whole module to touch
 * a given hierarchy (an isolated {@code -Dtest=} run, or - since Maven Surefire runs
 * classes in this module in roughly declaration/alphabetical order - whichever class
 * happens to sort first).
 * <p>
 * Every test class in this module that starts a {@code Recording} enabling one of these
 * event types should call {@link #warmup()} once from a {@code @BeforeAll} method, which
 * absorbs the race harmlessly for both hierarchies (a bare commit with no active
 * recording is a no-op besides this).
 */
final class JfrTestSupport {

	private JfrTestSupport() {
	}

	static void warmup() {
		new RainbowGumLogEvent.TraceEvent().commit();
		new RainbowGumLogEvent.DebugEvent().commit();
		new RainbowGumLogEvent.InfoEvent().commit();
		new RainbowGumLogEvent.WarnEvent().commit();
		new RainbowGumLogEvent.ErrorEvent().commit();
		new RainbowGumAlertEvent.TraceEvent().commit();
		new RainbowGumAlertEvent.DebugEvent().commit();
		new RainbowGumAlertEvent.InfoEvent().commit();
		new RainbowGumAlertEvent.WarnEvent().commit();
		new RainbowGumAlertEvent.ErrorEvent().commit();
	}

}
