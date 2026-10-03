package io.jstach.rainbowgum.otlp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/*
 * Minimal protobuf wire format writer, enough for the OTLP logs messages. Nested
 * messages are written to their own writer and then appended length prefixed.
 */
final class ProtoWriter {

	private static final int VARINT = 0;

	private static final int I64 = 1;

	private static final int LEN = 2;

	private byte[] buf = new byte[256];

	private int size;

	byte[] toByteArray() {
		return Arrays.copyOf(buf, size);
	}

	int size() {
		return size;
	}

	private void ensure(int extra) {
		if (size + extra > buf.length) {
			buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + extra));
		}
	}

	private void tag(int field, int wireType) {
		varint(((long) field << 3) | wireType);
	}

	private void varint(long value) {
		ensure(10);
		while ((value & ~0x7FL) != 0) {
			buf[size++] = (byte) ((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		buf[size++] = (byte) value;
	}

	private void raw(byte[] bytes, int off, int len) {
		ensure(len);
		System.arraycopy(bytes, off, buf, size, len);
		size += len;
	}

	ProtoWriter varintField(int field, long value) {
		tag(field, VARINT);
		varint(value);
		return this;
	}

	ProtoWriter fixed64Field(int field, long value) {
		tag(field, I64);
		ensure(8);
		for (int i = 0; i < 8; i++) {
			buf[size++] = (byte) (value >>> (8 * i));
		}
		return this;
	}

	ProtoWriter bytesField(int field, byte[] bytes) {
		tag(field, LEN);
		varint(bytes.length);
		raw(bytes, 0, bytes.length);
		return this;
	}

	ProtoWriter stringField(int field, String value) {
		return bytesField(field, value.getBytes(StandardCharsets.UTF_8));
	}

	ProtoWriter messageField(int field, ProtoWriter message) {
		tag(field, LEN);
		varint(message.size);
		raw(message.buf, 0, message.size);
		return this;
	}

}
