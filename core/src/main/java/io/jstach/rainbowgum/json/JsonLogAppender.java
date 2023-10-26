package io.jstach.rainbowgum.json;

import static io.jstach.rainbowgum.json.RawJsonWriter.COMMA;
import static io.jstach.rainbowgum.json.RawJsonWriter.OBJECT_END;
import static io.jstach.rainbowgum.json.RawJsonWriter.OBJECT_START;
import static io.jstach.rainbowgum.json.RawJsonWriter.SEMI;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogOutput;

public class JsonLogAppender implements LogAppender {

	/*
	 * { "version": "1.1", "host": "example.org", "short_message":
	 * "A short message that helps you identify what is going on", "full_message":
	 * "Backtrace here\n\nmore stuff", "timestamp": 1385053862.3072, "level": 1,
	 * "_user_id": 9001, "_some_info": "foo", "_some_env_var": "bar" }
	 */

	private final String host;

	private final KeyValues headers;

	private final RawJsonWriter raw = new RawJsonWriter(1024 * 8);

	private final StringBuilder formattedMessage = new StringBuilder();

	private final LogOutput out;

	private final boolean prettyprint;

	private final LevelFormatter levelFormatter = LevelFormatter.of();

	private static final int EXTENDED_F = 0x00000002;

	private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_INSTANT;

	public JsonLogAppender(String host, KeyValues headers, LogOutput out, boolean prettyprint) {
		super();
		this.host = host;
		this.headers = headers;
		this.out = out;
		this.prettyprint = prettyprint;
	}

	@Override
	public void append(LogEvent event) {
		raw.reset();
		formattedMessage.setLength(0);
		final String host = this.host;
		event.formattedMessage(formattedMessage);
		final String shortMessage = formattedMessage.toString();
		Instant now = event.timestamp();
		final double timeStamp = ((double) now.toEpochMilli()) / 1000;
		@Nullable
		String fullMessage = null;
		var t = event.throwable();
		if (t != null) {
			StringWriter sw = new StringWriter();
			sw.write(shortMessage);
			sw.write("\n");
			t.printStackTrace(new PrintWriter(sw));
			fullMessage = sw.toString();
		}
		int level = levelToSyslogLevel(event.level());
		raw.writeByte(OBJECT_START);
		int index = 0;
		index = write("host", host, index);
		index = write("short_message", shortMessage, index);
		index = write("full_message", fullMessage, index);
		index = writeDouble("timestamp", timeStamp, index, 0);
		index = writeInt("level", level, index, 0);
		index = write("_time", timeFormatter.format(now), index);
		index = write("_level", levelFormatter.format(event.level()), index);
		index = write("_logger", event.loggerName(), index);
		index = write("_thread_name", event.threadName(), index);
		index = write("_thread_id", String.valueOf(event.threadId()), index);

		if (t != null) {
			String tn = t.getClass().getCanonicalName();
			if (tn == null) {
				tn = t.getClass().getName();
			}
			index = write("_throwable", tn, index);
		}

		var kvs = event.keyValues();

		/*
		 * output headers
		 */
		for (int i = headers.start(); i >= 0; i = headers.next(i)) {
			String k = headers.key(i);
			if (kvs.getValue(k) != null) {
				continue;
			}
			String v = headers.value(i);
			index = write(k, v, index, EXTENDED_F);
		}

		/*
		 * output MDC
		 */
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			String k = kvs.key(i);
			String v = kvs.value(i);
			index = write(k, v, index, EXTENDED_F);
		}

		index = write("version", "1.1", index);

		if (index > 0 && prettyprint) {
			raw.writeAscii("\n");
		}
		raw.writeByte(OBJECT_END);
		raw.writeAscii("\n");
		raw.write(out, event);

	}

	private final int write(String k, @Nullable String v, int index) {
		return write(k, v, index, 0);
	}

	private final int write(String k, @Nullable String v, int index, int flag) {
		if (v == null)
			return index;
		_writeStartField(k, index, flag);
		raw.writeString(v);
		_writeEndField(flag);
		return index + 1;

	}

	private final int writeDouble(String k, double v, int index, int flag) {
		_writeStartField(k, index, flag);
		raw.writeDouble(v);
		_writeEndField(flag);
		return index + 1;
	}

	private final int writeInt(String k, int v, int index, int flag) {
		_writeStartField(k, index, flag);
		raw.writeInt(v);
		_writeEndField(flag);
		return index + 1;
	}

	private final void _writeStartField(String k, int index, int flag) {
		if (index > 0) {
			raw.writeByte(COMMA);
		}
		if (prettyprint) {
			raw.writeAscii("\n");
		}
		if (prettyprint) {
			raw.writeAscii(" ");
		}
		if ((flag & EXTENDED_F) == EXTENDED_F) {
			raw.writeAsciiString("_");
		}
		raw.writeAsciiString(k);
		raw.writeByte(SEMI);
	}

	private final void _writeEndField(int flag) {

	}

	private int levelToSyslogLevel(Level level) {
		/*
		 * FROM LOGBACK The syslog severity of a logging event is converted from the level
		 * of the logging event. The DEBUG level is converted to 7, INFO is converted to
		 * 6, WARN is converted to 4 and ERROR is converted to 3.
		 */
		int r = switch (level) {
			case ERROR -> 3;
			case DEBUG -> 7;
			case INFO -> 6;
			case TRACE -> 7;
			case WARNING -> 4;
			case ALL -> 7;
			case OFF -> 0;
		};
		return r;
	}

	@Override
	public void close() {
		out.close();
	}

}
