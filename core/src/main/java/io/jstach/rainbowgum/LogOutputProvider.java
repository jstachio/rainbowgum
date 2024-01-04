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
	 * @param name name of output.
	 * @param properties key value config.
	 * @return output.
	 * @throws IOException if unable to use the URI.
	 * @see LogProperties#of(URI)
	 */
	LogOutput output(URI uri, String name, LogProperties properties) throws IOException;

}
