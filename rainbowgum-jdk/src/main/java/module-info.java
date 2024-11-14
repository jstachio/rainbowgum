/**
 * Rainbow Gum JDK components. This module provides special integration and
 * adapters for the built-in JDK logging facilities. The impetus for this is
 * these logging facilities can be used very early in the JDK boot processes
 * well before logging has fully initialized. Furthermore the JDK
 * itself recommends that 
 * {@link java.lang.System.LoggerFinder#LoggerFinder() heavy initialization should not happen}.
 * 
 * <p>
 * The integration will make sure that neither the System.Logger or
 * java.util.logging will initialize Rainbow Gum too early by queueing the
 * events if a 
 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumEagerLoad} 
 * implementation is found (SLF4J facade implements this to indicate it will load Rainbow Gum). 
 * When a Rainbow Gum initializes and
 * {@linkplain io.jstach.rainbowgum.RainbowGum#set(Supplier) set as global} the
 * events will be replayed. If the events level are equal to
 * {@link java.lang.System.Logger.Level#ERROR} and a normal Rainbow Gum has not
 * been bound the messages will be printed to <code>System.err</code>. The idea
 * is something catastrophic has happened that will probably cause Rainbow Gum
 * to never load and thus never replay the events and you will not be able to
 * figure out what happened. If no
 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumEagerLoad} 
 * is found the SystemLogger will initialize Rainbow Gum. You can change the queuing
 * threshold and error level outputting with System properties:
 * </p>
 * <ul>
 * <li>{@value io.jstach.rainbowgum.LogProperties#GLOBAL_QUEUE_LEVEL_PROPERTY} = level</li>
 * <li>{@value io.jstach.rainbowgum.LogProperties#GLOBAL_QUEUE_ERROR_PROPERTY} = level</li>
 * </ul>
 * <p>
 * SLF4J does <a href="https://www.slf4j.org/manual.html#jep264">provide an
 * adapter/bridge for the System.Logger
 * (org.slf4j:slf4j-jdk-platform-logging)</a> but its use may cause Rainbow Gum
 * to initialize too early. <em>However that maybe desirable if</em>:
 * <ul>
 * <li>You are sure that Rainbow Gum can initialize early</li>
 * <li>Your application uses System.Logger (the SLF4J adapter will initialize
 * Rainbow Gum on Sytem.Logger usage if using  <code>io.jstach.rainbowgum.slf4j</code> module)</li>
 * </ul>
 * An alternative to using the SLF4J bridge if eager initialization is desired is
 * to set a System property with 
 * <code>{@value io.jstach.rainbowgum.jdk.systemlogger.SystemLoggingFactory#INITIALIZE_RAINBOW_GUM_PROPERTY}</code> 
 * to
 * the values in {@link io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder.InitOption}.
 * however that maybe difficult if one cannot set system properties before loading logging.
 * <p>
 * To disable installation of the java.util.logging handler set the property:
 * {@value io.jstach.rainbowgum.jdk.jul.JULConfigurator#JUL_DISABLE_PROPERTY}
 * to <code>true</code>. Alternatively if in a custom modular environment using jlink and
 * the module <code>java.logging</code> is not included the handler will not be installed.
 * Furthermore
 * <strong>the module <code>java.logging</code> is not required and thus
 * jlink might not automatically include it as it is <code>requires static</code>.
 * </strong>
 * </p>
 * 
 * <em> <strong>NOTE:</strong> While the JDK System.Logger is good for low level
 * libraries it's API (and Rainbow Gum implementation) is not designed for
 * performance. For applications and frameworks that do a lot of logging the
 * SLF4J facade is the preferred choice. </em>
 * <p>
 * Because the logger names of System.Logger and JUL are far less likely
 * to be actual class names and could be anything 
 * (unlike SLF4J which encourages class names and static loggers) Rainbow Gum
 * does not cache System.Loggers.
 * 
 * @provides System.LoggerFinder
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.jdk {

	exports io.jstach.rainbowgum.jdk.jul;

	requires io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.systemlogger;
	
	/*
	 * TODO perhaps a separate module for
	 * the java.logging handler
	 */
	requires static java.logging;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;

	provides System.LoggerFinder with io.jstach.rainbowgum.jdk.systemlogger.SystemLoggingFactory;
	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with io.jstach.rainbowgum.jdk.jul.JULConfigurator;
}