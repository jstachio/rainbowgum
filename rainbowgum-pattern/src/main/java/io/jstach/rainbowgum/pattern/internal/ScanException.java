package io.jstach.rainbowgum.pattern.internal;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when the pattern tokenizer encounters malformed input.
 */
public class ScanException extends RuntimeException {

	private static final long serialVersionUID = -3132040414328475658L;

	private @Nullable Throwable cause;

	public ScanException(String msg) {
		super(msg);
	}

	public ScanException(String msg, Throwable rootCause) {
		super(msg);
		this.cause = rootCause;
	}

	/*
	 * cause is only ever assigned in the constructor above, never mutated afterward
	 * (unlike Throwable's own initCause()-based mechanism, which this class does not
	 * use), so Throwable's synchronized getCause() has nothing to synchronize against
	 * here.
	 */
	@Override
	@SuppressWarnings("UnsynchronizedOverridesSynchronized")
	public @Nullable Throwable getCause() {
		return cause;
	}

}
