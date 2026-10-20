package io.jstach.rainbowgum.jul.logmanager;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/*
 * The globally bound Rainbow Gum and the MessageFormat style its properties choose, kept
 * current by RainbowGum.onGlobalChange. Loggers created before Rainbow Gum starts follow
 * it, and reading it takes no lock. The same class is in
 * rainbowgum-systemlogger, rainbowgum-jboss-logging and rainbowgum-jul-logmanager.
 */
final class GlobalRainbowGum implements Consumer<RainbowGum> {

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
	 * The caller must keep a strong reference as onGlobalChange only keeps a weak one.
	 */
	static GlobalRainbowGum of() {
		var global = new GlobalRainbowGum(StandardMessageFormatter.JUL);
		RainbowGum.onGlobalChange(global);
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
	public synchronized void accept(RainbowGum pushedGum) {
		pushed = true;
		set(pushedGum);
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
