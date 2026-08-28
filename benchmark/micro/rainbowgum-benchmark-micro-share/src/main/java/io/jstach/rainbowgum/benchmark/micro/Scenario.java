package io.jstach.rainbowgum.benchmark.micro;

import org.slf4j.Logger;

/**
 * One logging call shape, run repeatedly by {@link Runner}. All argument values are fixed
 * constants (not randomized) so every framework does identical work.
 */
public enum Scenario {

	/**
	 * A plain message with no arguments.
	 */
	NO_ARG {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.info("benchmark message with no arguments describing a simple event happening");
		}
	},
	/**
	 * A message with a single {} argument.
	 */
	ONE_ARG {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.info("benchmark message with one argument: name={}", NAME);
		}
	},
	/**
	 * A message with two {} arguments (SLF4J's two-arg overload, not the varargs one).
	 */
	TWO_ARG {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.info("benchmark message with two arguments: name={} count={}", NAME, COUNT);
		}
	},
	/**
	 * A message with three {} arguments (forces SLF4J's varargs overload).
	 */
	THREE_ARG {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.info("benchmark message with three arguments: name={} count={} ratio={}", NAME, COUNT, RATIO);
		}
	},
	/**
	 * Just the enabled-check, no logging call at all - isolates the cost of checking
	 * whether a level is enabled from the cost of a full disabled call.
	 */
	LEVEL_CHECK {
		@Override
		public void run(Logger log, Blackhole bh) {
			bh.consume(log.isDebugEnabled());
		}
	},
	/**
	 * A full call at a disabled level (root is configured at INFO) - the message and
	 * arguments are never formatted or written; this is the "mostly noop" case, matching
	 * how a lot of real production DEBUG/TRACE statements behave.
	 */
	DISABLED {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.debug("this message is disabled at the configured level: name={} count={}", NAME, COUNT);
		}
	},
	/**
	 * The SLF4J 2.x fluent event builder API ({@code Logger.atInfo()...}), rather than
	 * the classic varargs methods.
	 */
	EVENT_BUILDER {
		@Override
		public void run(Logger log, Blackhole bh) {
			log.atInfo()
				.setMessage("benchmark message via the SLF4J fluent event builder api: name={} count={}")
				.addArgument(NAME)
				.addArgument(COUNT)
				.log();
		}
	};

	private static final String NAME = "widget-42";

	private static final int COUNT = 7;

	private static final double RATIO = 0.9182;

	/**
	 * Runs one iteration of this scenario.
	 * @param log logger under test.
	 * @param bh sink for scenarios (like {@link #LEVEL_CHECK}) that would otherwise have
	 * no observable side effect for the JIT to anchor on.
	 */
	public abstract void run(Logger log, Blackhole bh);

}
