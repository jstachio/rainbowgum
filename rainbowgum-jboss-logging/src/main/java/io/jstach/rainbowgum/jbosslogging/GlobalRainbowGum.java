package io.jstach.rainbowgum.jbosslogging;

import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/*
 * The globally bound Rainbow Gum and the MessageFormat style its properties choose, kept
 * current by RainbowGum.onGlobalStateChange. Loggers created before Rainbow Gum starts follow
 * it, and reading it takes no lock. The same class is in
 * rainbowgum-systemlogger, rainbowgum-jboss-logging and rainbowgum-jul-logmanager.
 */
final class GlobalRainbowGum implements Consumer<Optional<RainbowGum>> {

	private volatile @Nullable RainbowGum gum;

	private volatile LogMessageFormatter messageFormatter;

	/*
	 * Guarded by this. Once a gum is pushed the one found while subscribing may be stale.
	 */
	private boolean pushed;

	private GlobalRainbowGum(LogMessageFormatter messageFormatter) {
		this.messageFormatter = messageFormatter;
	}

	/*
	 * The caller must keep a strong reference as onGlobalStateChange only keeps a weak
	 * one.
	 */
	static GlobalRainbowGum of() {
		var global = new GlobalRainbowGum(StandardMessageFormatter.JUL);
		RainbowGum.onGlobalStateChange(global);
		global.found(RainbowGum.getOrNull());
		return global;
	}

	@Nullable RainbowGum gum() {
		return gum;
	}

	LogMessageFormatter messageFormatter() {
		return messageFormatter;
	}

	private synchronized void found(@Nullable RainbowGum current) {
		if (!pushed) {
			if (current != null) {
				set(current);
			}
			else {
				messageFormatter = LogMessageFormatter.ofJUL(LogProperties.StandardProperties.SYSTEM_PROPERTIES);
			}
		}
	}

	@Override
	public synchronized void accept(Optional<RainbowGum> state) {
		pushed = true;
		var current = state.orElse(null);
		if (current == null) {
			// Drop the old gum even if system-property validation fails.
			this.gum = null;
			this.messageFormatter = StandardMessageFormatter.JUL;
			this.messageFormatter = LogMessageFormatter.ofJUL(LogProperties.StandardProperties.SYSTEM_PROPERTIES);
		}
		else {
			set(current);
		}
	}

	private void set(RainbowGum current) {
		LogMessageFormatter formatter;
		RuntimeException failure = null;
		try {
			formatter = LogMessageFormatter.ofJUL(current.config().properties());
		}
		catch (RuntimeException e) {
			failure = e;
			formatter = StandardMessageFormatter.JUL;
		}
		this.messageFormatter = formatter;
		this.gum = current;
		if (failure != null) {
			/* Alert listeners may bind another gum; publish our state first. */
			current.config().alerts().error(GlobalRainbowGum.class, failure);
		}
	}

}
