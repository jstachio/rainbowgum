package io.jstach.rainbowgum;

import java.io.IOException;
import java.net.URI;

/**
 * Finds output based on URI.
 */
public interface LogOutputProvider {

	/**
	 * Loads an output from a URI.
	 * @param uri uri.
	 * @return output.
	 * @throws IOException if unable to use the URI.
	 * @see LogProperties#of(URI)
	 */
	LogOutput output(URI uri) throws IOException;

}
