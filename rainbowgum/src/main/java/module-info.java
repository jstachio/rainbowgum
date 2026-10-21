/**
 * RainbowGum default grouping of modules transitively pulled
 * in from Maven and module system.
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum")
module io.jstach.rainbowgum.bundle {
	requires static io.jstach.rainbowgum.annotation;
	requires io.jstach.rainbowgum;
	requires static org.jspecify;
	/*
	 * The following static is to placate maven javadoc
	 * issues with scope compile.
	 */
	requires static io.jstach.rainbowgum.slf4j;
}