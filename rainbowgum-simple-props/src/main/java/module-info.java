import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Loads {@link io.jstach.rainbowgum.LogProperties} from system properties, environment
 * variables, profile resources, and a classpath {@code logging.properties} resource.
 * See {@link io.jstach.rainbowgum.simple.props.SimpleProperties} for profile selection
 * and precedence.
 * @provides RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.simple.props {

	exports io.jstach.rainbowgum.simple.props;

	requires transitive io.jstach.rainbowgum;

	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.jspecify;

	provides RainbowGumServiceProvider with io.jstach.rainbowgum.simple.props.SimplePropertiesProvider;

}
