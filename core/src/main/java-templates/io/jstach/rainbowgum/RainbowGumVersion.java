package io.jstach.rainbowgum;

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
	 * Resolves the Rainbow Gum documentation URL based on {@link #VERSION}. Only released
	 * versions are published there, so the URL does not resolve for a snapshot version.
	 * @return URL <strong>with no trailing slash!</strong>
	 */
	public static String documentBaseUrl() {
		/*
		 * Deliberately unconditional. Snapshot doc does exist, but at
		 * https://jstach.io/rainbowgum (no version, no /apidocs); handling it would be:
		 * if (VERSION.endsWith("-SNAPSHOT")) return "https://jstach.io/rainbowgum";
		 */
		return "https://jstach.io/doc/rainbowgum/" + VERSION + "/apidocs";
	}
}
