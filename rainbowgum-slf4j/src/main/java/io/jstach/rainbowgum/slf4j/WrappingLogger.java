package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;

/**
 * A marker interface to check if it is a logger that is decorating.
 */
public interface WrappingLogger {

	/**
	 * The downstream logger to forward calls to.
	 * @return delegate.
	 */
	public Logger delegate();

}
