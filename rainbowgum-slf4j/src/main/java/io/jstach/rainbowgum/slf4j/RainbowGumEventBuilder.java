package io.jstach.rainbowgum.slf4j;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Marker;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAware;

class RainbowGumEventBuilder implements LoggingEventBuilder, DepthAware {

	private final BaseLogger logger;

	private final RainbowGumMDCAdapter mdc;

	@Nullable
	private List<@Nullable Object> args;

	private @Nullable MutableKeyValues mutableKeyValues;

	private final String loggerName;

	private final Level level;

	private String message = "";

	@Nullable
	private Throwable throwable;

	private static final LogMessageFormatter messageFormatter = StandardMessageFormatter.SLF4J;

	private static final int DEPTH = 5;

	private int depth = 0;

	public RainbowGumEventBuilder(BaseLogger logger, RainbowGumMDCAdapter mdc, Level level) {
		this.logger = logger;
		this.mdc = mdc;
		this.level = level;
		this.loggerName = logger.loggerName();
	}

	@Override
	public void setDepth(int index, int endIndex) {
		this.depth = index;

	}

	MutableKeyValues kvs() {
		var kvs = this.mutableKeyValues;
		if (kvs == null) {
			var copy = mdc.mutableKeyValuesOrNull();
			if (copy == null) {
				copy = MutableKeyValues.of();
			}
			else {
				copy = copy.copy();
			}
			kvs = this.mutableKeyValues = copy;
		}
		return kvs;
	}

	List<@Nullable Object> args() {
		var args = this.args;
		if (args == null) {
			args = this.args = new ArrayList<>(2);
		}
		return args;
	}

	@Override
	public LoggingEventBuilder setCause(Throwable cause) {
		this.throwable = cause;
		return this;
	}

	@Override
	public LoggingEventBuilder addMarker(Marker marker) {
		return this;
	}

	@Override
	public LoggingEventBuilder addArgument(Object p) {
		args().add(p);
		return this;
	}

	@Override
	public LoggingEventBuilder addArgument(Supplier<?> objectSupplier) {
		return addArgument(objectSupplier.get());
	}

	@Override
	public LoggingEventBuilder addKeyValue(String key, Object value) {
		kvs().putKeyValue(key, value.toString());
		return this;
	}

	@Override
	public LoggingEventBuilder addKeyValue(String key, Supplier<Object> valueSupplier) {
		return addKeyValue(key, valueSupplier.get());
	}

	@Override
	public LoggingEventBuilder setMessage(String message) {
		this.message = Objects.requireNonNullElse(message, "");
		return this;
	}

	@Override
	public LoggingEventBuilder setMessage(Supplier<String> messageSupplier) {
		return setMessage(messageSupplier.get());
	}

	private void _log() {
		Instant timestamp = Instant.now();
		var thread = Thread.currentThread();
		String threadName = thread.getName();
		long threadId = thread.threadId();
		KeyValues keyValues = this.mutableKeyValues;
		if (keyValues == null) {
			keyValues = mdc.mutableKeyValuesOrNull();
			if (keyValues == null) {
				keyValues = KeyValues.of();
			}
		}
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, this.args);
		logger.handle(event, DEPTH + this.depth);
	}

	@Override
	public void log() {
		_log();
	}

	@Override
	public void log(String message) {
		this.message = Objects.requireNonNullElse(message, "");
		_log();
	}

	@Override
	public void log(String message, Object arg) {
		setMessage(message);
		addArgument(arg);
		_log();
	}

	@Override
	public void log(String message, Object arg0, Object arg1) {
		setMessage(message);
		var args = args();
		args.add(arg0);
		args.add(arg1);
		_log();
	}

	@Override
	public void log(String message, Object... args) {
		setMessage(message);
		var args_ = args();
		if (args != null) {
			for (var a : args) {
				args_.add(a);
			}
		}
		_log();
	}

	@Override
	public void log(Supplier<String> messageSupplier) {
		setMessage(messageSupplier);
		_log();
	}

}
