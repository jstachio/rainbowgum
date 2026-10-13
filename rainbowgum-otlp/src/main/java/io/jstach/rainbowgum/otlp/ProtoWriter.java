package io.jstach.rainbowgum.otlp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/*
 * Minimal protobuf wire format writer, enough for the OTLP logs messages. A nested message
 * is written in place between startMessage and endMessage; endMessage then shifts it right
 * to make room for its length prefix. That keeps a whole request in one buffer, instead of
 * a buffer per nested message copied into its parent.
 */
final class ProtoWriter {

	private static final int VARINT = 0;

	private static final int I64 = 1;

	private static final int LEN = 2;

	private byte[] buf;

	private int size;

	ProtoWriter(int capacity) {
		this.buf = new byte[capacity];
	}

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

	/*
	 * Starts a length delimited message field and returns the position its content starts
	 * at, to pass to endMessage.
	 */
	int startMessage(int field) {
		tag(field, LEN);
		return size;
	}

	void endMessage(int start) {
		int length = size - start;
		int prefix = varintSize(length);
		ensure(prefix);
		System.arraycopy(buf, start, buf, start + prefix, length);
		int p = start;
		long value = length;
		while ((value & ~0x7FL) != 0) {
			buf[p++] = (byte) ((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		buf[p] = (byte) value;
		size += prefix;
	}

	private static int varintSize(int value) {
		int n = 1;
		while ((value & ~0x7F) != 0) {
			n++;
			value >>>= 7;
		}
		return n;
	}

}
