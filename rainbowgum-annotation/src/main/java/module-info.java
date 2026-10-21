/**
 * Rainbow Gum annotations for code generation and API documentation.
 * This module provides configuration builder annotations and source-level Maven module metadata.
 * It is separated from core so that code generation can be done on the core module.
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-annotation")
module io.jstach.rainbowgum.annotation {
	exports io.jstach.rainbowgum.annotation;
}
