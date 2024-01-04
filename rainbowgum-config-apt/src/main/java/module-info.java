import javax.annotation.processing.Processor;

/**
 * Rainbow Gum annotation processors.
 * @provides Processor with config processor.
 */
module io.jstach.rainbowgum.apt {
	requires jdk.compiler;
	requires static io.jstach.prism;
	requires static io.jstach.svc;
	requires static io.jstach.rainbowgum;
	requires static io.jstach.jstache;
	requires static org.eclipse.jdt.annotation;
	
	provides Processor with io.jstach.rainbowgum.apt.ConfigProcessor;
}