package io.jstach.rainbowgum.slf4j;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.internal.FacadeLogEvent;

final class MutableEvent implements FacadeLogEvent {

	Instant timestamp = Instant.MIN;

	String threadName = "";

	long threadId = 0;

	final String loggerName;

	final Level level;

	String message = "";

	@Nullable
	Throwable throwable;

	KeyValues keyValues = KeyValues.of();

	List<@Nullable Object> args = Collections.emptyList();

	public MutableEvent(String loggerName, Level level) {
		super();
		this.loggerName = loggerName;
		this.level = level;
	}

	@Override
	public Instant timestamp() {
		var ts = this.timestamp;
		if (ts == null) {
			ts = this.timestamp = Instant.now();
		}
		return ts;
	}

	@Override
	public String threadName() {
		var tn = this.threadName;
		if (tn == null) {
			tn = this.threadName = Thread.currentThread().getName();
		}
		return tn;
	}

	@Override
	public long threadId() {
		var ti = this.threadId;
		if (ti == 0) {
			ti = this.threadId = Thread.currentThread().threadId();
		}
		return ti;
	}

	@Override
	public Level level() {
		return this.level;
	}

	@Override
	public String loggerName() {
		return this.loggerName;
	}

	@Override
	public String message() {
		return this.message;
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		var args = this.args;
		if (args == null) {
			sb.append(message);
		}
		else {
			int size = args.size();
			switch (size) {
				case 0 -> sb.append(message);
				case 1 -> StandardMessageFormatter.SLF4J.format(sb, message, args.get(0));
				case 2 -> StandardMessageFormatter.SLF4J.format(sb, message, args.get(0), args.get(1));
				default -> StandardMessageFormatter.SLF4J.formatArray(sb, message, args.toArray());
			}
		}
	}

	@Override
	public @Nullable Throwable throwableOrNull() {
		return this.throwable;
	}

	@Override
	public KeyValues keyValues() {
		return this.keyValues;
	}

	@Override
	public LogEvent freeze() {
		return freeze(timestamp());
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		String threadName = threadName();
		Long threadId = threadId();
		return LogEvent
			.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
					StandardMessageFormatter.SLF4J, args)
			.freeze();
	}

}
