package io.jstach.rainbowgum.json;

import io.jstach.rainbowgum.LogEncoder.Buffer;

import static io.jstach.rainbowgum.json.RawJsonWriter.COMMA;
import static io.jstach.rainbowgum.json.RawJsonWriter.SEMI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

class JsonBuffer implements Buffer {

	private final RawJsonWriter jsonWriter = new RawJsonWriter(1024 * 8);

	private final StringBuilder formattedMessageBuilder = new StringBuilder();

	private final boolean prettyprint;

	public static final int EXTENDED_F = 0x00000002;

	public JsonBuffer(boolean prettyprint) {
		super();
		this.prettyprint = prettyprint;
	}

	@Override
	public void drain(LogOutput output, LogEvent event) {
		jsonWriter.write(output, event);
		clear();
	}

	@Override
	public void clear() {
		jsonWriter.reset();
		formattedMessageBuilder.setLength(0);
	}

	public RawJsonWriter getJsonWriter() {
		return jsonWriter;
	}

	public StringBuilder getFormattedMessageBuilder() {
		return formattedMessageBuilder;
	}

	public final int write(String k, @Nullable String v, int index) {
		return write(k, v, index, 0);
	}

	public final int write(String k, @Nullable String v, int index, int flag) {
		if (v == null)
			return index;
		_writeStartField(k, index, flag);
		jsonWriter.writeString(v);
		_writeEndField(flag);
		return index + 1;

	}

	public final int writeDouble(String k, double v, int index, int flag) {
		_writeStartField(k, index, flag);
		jsonWriter.writeDouble(v);
		_writeEndField(flag);
		return index + 1;
	}

	public final int writeInt(String k, int v, int index, int flag) {
		_writeStartField(k, index, flag);
		jsonWriter.writeInt(v);
		_writeEndField(flag);
		return index + 1;
	}

	private final void _writeStartField(String k, int index, int flag) {
		if (index > 0) {
			jsonWriter.writeByte(COMMA);
		}
		if (prettyprint) {
			jsonWriter.writeAscii("\n");
		}
		if (prettyprint) {
			jsonWriter.writeAscii(" ");
		}
		if ((flag & EXTENDED_F) == EXTENDED_F) {
			jsonWriter.writeAsciiString("_");
		}
		jsonWriter.writeAsciiString(k);
		jsonWriter.writeByte(SEMI);
	}

	private final void _writeEndField(int flag) {

	}

}
