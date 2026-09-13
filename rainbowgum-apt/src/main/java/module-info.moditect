/**
 * Rainbow Gum annotation processors.
 * @provides javax.annotation.processing.Processor with config processor.
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
	provides javax.annotation.processing.Processor with io.jstach.rainbowgum.apt.ConfigProcessor;
}