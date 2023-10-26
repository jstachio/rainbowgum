package io.jstach.rainbowgum.json;

import static io.jstach.rainbowgum.json.JsonBuffer.EXTENDED_F;
import static io.jstach.rainbowgum.json.RawJsonWriter.OBJECT_END;
import static io.jstach.rainbowgum.json.RawJsonWriter.OBJECT_START;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogOutput;

public class GelfEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	private final String host;

	private final KeyValues headers;

	private final boolean prettyprint;

	private final LevelFormatter levelFormatter = LevelFormatter.of();

	private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_INSTANT;

	public GelfEncoder(String host, KeyValues headers, LogOutput out, boolean prettyprint) {
		super();
		this.host = host;
		this.headers = headers;
		this.prettyprint = prettyprint;
	}

	@Override
	protected JsonBuffer doBuffer() {
		return new JsonBuffer(this.prettyprint);
	}

	@Override
	protected void doEncode(LogEvent event, JsonBuffer buffer) {
		buffer.clear();
		var raw = buffer.getJsonWriter();
		var formattedMessage = buffer.getFormattedMessageBuilder();
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
		index = buffer.write("host", host, index);
		index = buffer.write("short_message", shortMessage, index);
		index = buffer.write("full_message", fullMessage, index);
		index = buffer.writeDouble("timestamp", timeStamp, index, 0);
		index = buffer.writeInt("level", level, index, 0);
		index = buffer.write("_time", timeFormatter.format(now), index);
		index = buffer.write("_level", levelFormatter.format(event.level()), index);
		index = buffer.write("_logger", event.loggerName(), index);
		index = buffer.write("_thread_name", event.threadName(), index);
		index = buffer.write("_thread_id", String.valueOf(event.threadId()), index);

		if (t != null) {
			String tn = t.getClass().getCanonicalName();
			if (tn == null) {
				tn = t.getClass().getName();
			}
			index = buffer.write("_throwable", tn, index);
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
			index = buffer.write(k, v, index, EXTENDED_F);
		}

		/*
		 * output MDC
		 */
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			String k = kvs.key(i);
			String v = kvs.value(i);
			index = buffer.write(k, v, index, EXTENDED_F);
		}

		index = buffer.write("version", "1.1", index);

		if (index > 0 && prettyprint) {
			raw.writeAscii("\n");
		}
		raw.writeByte(OBJECT_END);
		raw.writeAscii("\n");
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

}
