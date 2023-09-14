package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public interface LogEncoder extends LogOutput {

	@Override
	default void append(
			String s) {
		encode(s.getBytes(StandardCharsets.UTF_8));
	}
	
	public void encode(byte[] bytes);
	
	public void encode(byte[] bytes, int off, int len);
	
	public static LogEncoder of(OutputStream out) {
		return new LogEncoder() {
			
			@Override
			public void encode(
					byte[] bytes,
					int off,
					int len) {
				try {
					out.write(bytes, off, len);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			
			@Override
			public void encode(
					byte[] bytes) {
				try {
					out.write(bytes);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				
			}
		};
	}
	
}
