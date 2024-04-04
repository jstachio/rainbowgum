package io.jstach.rainbowgum;

import java.io.PrintWriter;
import java.io.Writer;

import org.eclipse.jdt.annotation.Nullable;

/*
 * Internal Utilities.
 * I hate utility classes but a necessary evil I suppose.
 */
final class Internal {

	final static class StringBuilderWriter extends Writer {

		private final StringBuilder buf;

		/**
		 * Create a new string writer using the default initial string-buffer size.
		 */
		StringBuilderWriter(StringBuilder buf) {
			this.buf = buf;
			lock = buf;
		}

		/**
		 * Write a single character.
		 */
		@Override
		public void write(int c) {
			buf.append((char) c);
		}

		@Override
		public void write(char cbuf[], int off, int len) {
			if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
				throw new IndexOutOfBoundsException();
			}
			else if (len == 0) {
				return;
			}
			buf.append(cbuf, off, len);
		}

		/**
		 * Write a string.
		 */
		@Override
		public void write(String str) {
			buf.append(str);
		}

		@Override
		public void write(String str, int off, int len) {
			buf.append(str, off, off + len);
		}

		@Override
		public StringBuilderWriter append(@Nullable CharSequence csq) {
			write(String.valueOf(csq));
			return this;
		}

		@Override
		public StringBuilderWriter append(@Nullable CharSequence csq, int start, int end) {
			if (csq == null)
				csq = "null";
			return append(csq.subSequence(start, end));
		}

		@Override
		public StringBuilderWriter append(char c) {
			write(c);
			return this;
		}

		// /**
		// * Return the string buffer itself.
		// * @return StringBuffer holding the current buffer value.
		// */
		// public StringBuilder getBuffer() {
		// return buf;
		// }

		@Override
		public void flush() {
		}

		/**
		 * Closing a {@code StringWriter} has no effect. The methods in this class can be
		 * called after the stream has been closed without generating an
		 * {@code IOException}.
		 */
		@Override
		public void close() {
		}

	}

	/*
	 * This print writer will avoid using locks.
	 */
	final static class StringBuilderPrintWriter extends PrintWriter {

		private final StringBuilderWriter writer;

		public static StringBuilderPrintWriter of(StringBuilder builder) {
			return new StringBuilderPrintWriter(new StringBuilderWriter(builder));
		}

		public StringBuilderPrintWriter(StringBuilderWriter writer) {
			super(writer);
			this.writer = writer;
		}

		@Override
		public void write(@SuppressWarnings("null") String str) {
			writer.append(str);
		}

		@Override
		public void write(@SuppressWarnings("null") String str, int off, int len) {
			writer.write(str, off, off + len);
		}

		@Override
		public StringBuilderPrintWriter append(char c) {
			writer.append(c);
			return this;
		}

		@Override
		public StringBuilderPrintWriter append(@Nullable CharSequence csq) {
			writer.append(csq);
			return this;
		}

		@Override
		public StringBuilderPrintWriter append(@Nullable CharSequence csq, int start, int end) {
			writer.append(csq, start, end);
			return this;
		}

		@Override
		public void write(@SuppressWarnings("null") char[] buf, int off, int len) {
			writer.write(buf, off, len);
		}

	}

}
