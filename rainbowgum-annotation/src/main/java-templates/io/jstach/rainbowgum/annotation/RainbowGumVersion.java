package io.jstach.rainbowgum.annotation;

/**
 * Provides the version information of Rainbow Gum as static literals.
 */
public final class RainbowGumVersion {
	private RainbowGumVersion() {
	}
	/**
	 * Rainbow Gum Version.
	 */
	public static final String VERSION = "${project.version}";

	/**
	 * Resolves the Rainbow Gum documentation URL based on {@link #VERSION}.
	 * @return URL <strong>with no trailing slash!</strong>
	 */
	public static String documentBaseUrl() {
		String version = VERSION;
		if (version.endsWith("-SNAPSHOT")) {
			return "https://jstach.io/rainbowgum";
		}
		return "https://jstach.io/doc/rainbowgum/" + version + "/apidocs";
	}
}
