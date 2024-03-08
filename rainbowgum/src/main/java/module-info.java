/**
 * RainbowGum default grouping of modules transitively pulled
 * in from Maven and module system.
 */
module io.jstach.rainbowgum.bundle {
	requires io.jstach.rainbowgum;
	requires static org.eclipse.jdt.annotation;
	/*
	 * The following static is to placate maven javadoc
	 * issues with scope compile.
	 */
	requires static io.jstach.rainbowgum.pattern;
	requires static io.jstach.rainbowgum.slf4j;
}