package io.jstach.rainbowgum.test.aotcache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run twice by this module's {@code aot} profile (see its pom.xml): once with
 * {@code -XX:AOTCacheOutput=...} to train and assemble the JDK's AOT cache, once with
 * {@code -XX:AOTMode=on -XX:AOTCache=...} to actually load it. Both runs must produce
 * identical log output; the second run additionally proves the cache was really used,
 * since {@code AOTMode=on} fails the JVM outright rather than silently falling back if
 * the cache cannot be mapped.
 */
public final class Main {

	private Main() {
	}

	/**
	 * Entry point.
	 * @param args unused.
	 */
	public static void main(String[] args) {
		Logger log = LoggerFactory.getLogger("aot.cache.test");
		log.info("Hello from the JDK AOT cache!");
	}

}
