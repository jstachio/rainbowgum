package io.jstach.rainbowgum.file;

import java.nio.file.Path;

final class PropertiesPaths {

	private PropertiesPaths() {
	}

	/*
	 * A backslash is an escape in properties text, so a Windows path needs them doubled.
	 */
	static String escape(Path path) {
		return path.toString().replace("\\", "\\\\");
	}

}
