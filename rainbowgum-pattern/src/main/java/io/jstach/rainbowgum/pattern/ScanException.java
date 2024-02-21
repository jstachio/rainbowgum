package io.jstach.rainbowgum.pattern;

class ScanException extends RuntimeException {

	private static final long serialVersionUID = -3132040414328475658L;

	Throwable cause;

	public ScanException(String msg) {
		super(msg);
	}

	public ScanException(String msg, Throwable rootCause) {
		super(msg);
		this.cause = rootCause;
	}

	public Throwable getCause() {
		return cause;
	}

}
