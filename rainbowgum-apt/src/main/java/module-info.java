/**
 * Rainbow Gum annotation processors.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum-apt</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 * @provides javax.annotation.processing.Processor with config and enum alias processors.
 */
module io.jstach.rainbowgum.apt {
	requires jdk.compiler;
	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.prism;
	requires static io.jstach.svc;
	requires static org.jspecify;
	// only for pistachio-prism-apt's generated *Prism.java classes - see
	// rainbowgum-apt/pom.xml's comment on the org.eclipse.jdt.annotation dependency.
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.jstache;
	provides javax.annotation.processing.Processor with io.jstach.rainbowgum.apt.ConfigProcessor,
			io.jstach.rainbowgum.apt.EnumAliasProcessor;
}