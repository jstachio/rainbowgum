import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Loads {@link io.jstach.rainbowgum.LogProperties} from system properties, environment
 * variables, and a classpath {@code logging.properties} resource.
 * @provides RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.simple.props {

	exports io.jstach.rainbowgum.simple.props;

	requires transitive io.jstach.rainbowgum;

	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;

	provides RainbowGumServiceProvider with io.jstach.rainbowgum.simple.props.SimplePropertiesProvider;

}
