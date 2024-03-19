package io.jstach.rainbowgum.pattern.internal;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @hidden
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

	public @Nullable Throwable getCause() {
		return cause;
	}

}
