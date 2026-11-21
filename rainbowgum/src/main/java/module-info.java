/**
 * RainbowGum default grouping of modules transitively pulled
 * in from Maven and module system.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 */
module io.jstach.rainbowgum.bundle {
	requires io.jstach.rainbowgum;
	requires static org.jspecify;
	/*
	 * The following static is to placate maven javadoc
	 * issues with scope compile.
	 */
	requires static io.jstach.rainbowgum.slf4j;
}