package snippets;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.spi.AbstractFilteringLogger;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

// @start region="decoratorExample"
public class DecoratorExample extends LoggerDecoratorService {

	@Override
	public String name() {
		return "sampling";
	}

	@Override
	public Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth) {
		return new SamplingLogger(previousLogger);
	}

	static class SamplingLogger extends AbstractFilteringLogger {

		private final AtomicInteger debugCount = new AtomicInteger();

		SamplingLogger(DepthAwareLogger delegate) {
			super(delegate);
		}

		@Override
		protected boolean isEnabled(Level level, @Nullable Marker marker) {
			// Cheap pre-check: keep only every 10th DEBUG event, everything else
			// passes through unchanged. This runs before any LoggingEventBuilder
			// is built.
			if (level != Level.DEBUG) {
				return true;
			}
			return debugCount.incrementAndGet() % 10 == 0;
		}

		@Override
		protected boolean decorate(LoggingEventBuilder builder, @Nullable Marker marker) {
			if (marker != null) {
				// Rainbow Gum core has no built-in Marker support (it is not stored
				// on the event), so surface it as a key value instead.
				builder.addKeyValue("marker", marker.toString());
			}
			return true;
		}

	}

}
// @end
