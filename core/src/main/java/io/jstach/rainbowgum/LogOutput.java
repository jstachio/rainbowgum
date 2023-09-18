package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public sealed interface LogOutput extends AutoCloseable permits LogEncoder {

	void append(String s);

	default PrintWriter asWriter() {
		return toWriter(this);
	}

	public static PrintWriter toWriter(LogOutput out) {
		if (out instanceof PrintWriter w) {
			return w;
		}
		var writer = new Writer() {

			@Override
			public void write(String str) throws IOException {
				out.append(str);
			}

			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				out.append(String.valueOf(cbuf, off, len));
			}

			@Override
			public void flush() throws IOException {

			}

			@Override
			public void close() throws IOException {

			}
		};
		return new PrintWriter(writer);
	}

	void close();

}
