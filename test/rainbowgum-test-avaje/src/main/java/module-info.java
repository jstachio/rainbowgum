@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-test-avaje")
module io.jstach.rainbowgum.test.avaje {
	requires static io.jstach.rainbowgum.annotation;
	exports io.jstach.rainbowgum.test.avaje;
	requires io.avaje.config;
	requires org.slf4j;
	requires io.jstach.rainbowgum.scopedkeyvalues;
	requires static org.jspecify;
}