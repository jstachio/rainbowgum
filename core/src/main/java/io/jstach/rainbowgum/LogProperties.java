package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogProperties.Builder.AbstractLogProperties;
import io.jstach.rainbowgum.LogProperties.FoundProperty.ListProperty;
import io.jstach.rainbowgum.LogProperties.FoundProperty.MapProperty;
import io.jstach.rainbowgum.LogProperties.FoundProperty.StringProperty;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/**
 * Provides String based properties like {@link System#getProperty(String)} for default
 * configuration of logging levels and output. Since Rainbow Gum is configuration agnostic
 * LogProperties is a simple string key and string value interface to allow almost any
 * configuration implementation. The only required method to implement is
 * {@link #valueOrNull(String)} and thus LogProperties is a functional like interface but
 * using {@link LogProperties#builder()} is preferred so that a
 * {@linkplain #description(String) description} can be added.
 * <p>
 * If a custom {@link LogProperties} is not configured the default RainbowGum uses System
 * properties.
 * <p>
 * The built-in properties to configure RainbowGum are modeled after <a href=
 * "https://docs.spring.io/spring-boot/docs/3.1.0/reference/html/features.html#features.logging">
 * Spring Boot logging </a>
 * <p>
 * LogProperties should generally be accessed with the {@link LogProperty.PropertyGetter}
 * and {@link LogProperty.Property} like-monads which will allow safe
 * <em>programmatic</em> data conversion of the flat key values and useful error messages
 * to users if the property cannot be mapped or is missing. For more <em>declarative</em>
 * injection of properties see {@link LogConfigurable} which will generate builders that
 * can use LogProperties.
 * <p>
 * Rainbow Gum treats {@value #SEP} in the keys as special separator analogous to
 * JavaScript/JSON and various other configuration systems. Furthermore to work with a
 * wide set of configuration systems once a property key has a value it is
 * <strong>RECOMMENDED</strong> that there should not be other property key value with a
 * key that is a prefix of the previously mentioned key. For example allowing two
 * properties <code>logging.example=stuff</code>, <code>logging.example.a=A</code> is not
 * recommended as <code>logging.example</code> is defined as a leaf. The exception to this
 * rule is logging levels mapped to logger names for consistency with Spring Boot.
 * <p>
 * Rainbow Gum out of the the box supports parsing two formats into properties:
 * {@link Properties} text, and {@link URI#getRawQuery()} percent encoded query
 * parameters. The two different formats have different behavior for
 * {@link #listOrNull(String)} and {@link #mapOrNull(String)}. If a custom format of
 * LogProperties is created those two methods may need to be extended.
 * <p>
 * A convention used through out Rainbow Gum is to have the property names as constants as
 * it makes documentation easier. These constants name are suffixed with
 * <code>_PROPERTY</code> or <code>_PREFIX</code> the former being a fully qualified key
 * that maybe parameterized and the latter being a partial key ending with
 * "<code>.</code>". The fully qualified keys are like leaves on a tree.
 * <p>
 * Property keys can be parameterized by using
 * {@link LogProperties#interpolateKey(String, Map)} and a general pattern is
 * <code>logging.component.{name}.prop</code> where {@value #NAME} is a parameter. Many
 * property constants use this to allow multiple configuration of components by name.
 *
 * <table class="table">
 * <caption><strong>Built-in Property Patterns</strong></caption>
 * <tr>
 * <th>Property Pattern</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@value #LEVEL_PREFIX} + {@value #SEP} + logger = LEVEL</td>
 * <td>Sets the level for the logger. If logger name is missing then it is considered the
 * root logger. {@link Level#INFO} is the default level for the root logger.</td>
 * </tr>
 * <tr>
 * <td>{@value #CHANGE_PREFIX} + {@value #SEP} + logger = boolean |
 * List&lt;{@link ChangeType}&gt;</td>
 * <td>Allows runtime changing of levels for the logger. By design RainbowGum does not
 * allow loggers to change levels once initialized. This configuration will allow the
 * level to be changed for the logger and its children and by default is false as the
 * built-in {@link LogProperties} is generally static (system properties).</td>
 * </tr>
 * <tr>
 * <td>{@value #ROUTES_PROPERTY} = <code>List&lt;String&gt;</code></td>
 * <td>A comma separated list of routes that should be enabled. If not set
 * {@value LogRouter.Router#DEFAULT_ROUTER_NAME } is the default.</td>
 * </tr>
 * <tr>
 * <td>{@value #ROUTE_APPENDERS_PROPERTY} = <code>List&lt;String&gt;</code></td>
 * <td>A comma separated list of appenders associated with the route. If not set
 * {@value #APPENDERS_PROPERTY} is used.</td>
 * </tr>
 * <tr>
 * <td>{@value #ROUTE_PUBLISHER_PROPERTY} = <code>URI</code></td>
 * <td>Looks up a publisher by URI scheme using the {@link LogPublisherRegistry}. The
 * publisher is then configured by properties with {@value #PUBLISHER_PREFIX}.</td>
 * </tr>
 * <tr>
 * <td>{@value #APPENDERS_PROPERTY} = <code>List&lt;String&gt;</code></td>
 * <td>A comma separated list of appenders that should be enabled. If not set
 * {@value LogAppender#CONSOLE_APPENDER_NAME} is the default.</td>
 * </tr>
 * <tr>
 * <td>{@value #APPENDER_OUTPUT_PROPERTY } = <code>URI</code></td>
 * <td>Looks up an output by URI scheme using the {@link LogOutputRegistry}. The output is
 * then configured by properties with {@value #OUTPUT_PREFIX}.</td>
 * </tr>
 * <tr>
 * <td>{@value #APPENDER_ENCODER_PROPERTY } = <code>URI</code></td>
 * <td>Looks up an encoder by URI scheme using the {@link LogEncoderRegistry}. The encoder
 * is then configured by properties with {@value #ENCODER_PREFIX}.</td>
 * </tr>
 * <tr>
 * <tr>
 * <td>{@value #OUTPUT_PREFIX} + <code>propertyName</code></td>
 * <td>Configures the named output. The name of the output usually comes from the
 * appender.</td>
 * </tr>
 * <tr>
 * <td>{@value #ENCODER_PREFIX} + <code>propertyName</code></td>
 * <td>Configures the named encoder. The name of the encoder usually comes from the
 * appender.</td>
 * </tr>
 * <tr>
 * <td>{@value #FILE_PROPERTY} = URI</td>
 * <td>A URI or file path to log to a file. This is for Spring Boot compatibility.</td>
 * </tr>
 * </table>
 *
 * @see LogConfigurable
 */
public interface LogProperties {

	/**
	 * properties separator.
	 */
	static final String SEP = ".";

	/**
	 * Logging properties prefix.
	 */
	static final String ROOT_PREFIX = "logging" + SEP;

	/**
	 * Logging level properties prefix. The value should be the name of a
	 * {@linkplain java.lang.System.Logger.Level level}.
	 */
	static final String LEVEL_PREFIX = ROOT_PREFIX + "level";

	/**
	 * Logging change configuration allowed property.
	 */
	static final String GLOBAL_CHANGE_PROPERTY = ROOT_PREFIX + "global.change";

	/**
	 * Will globally turn of any ANSI escape output as well disable extensions that do
	 * things for ANSI escape like JANSI.
	 */
	static final String GLOBAL_ANSI_DISABLE_PROPERTY = ROOT_PREFIX + "global.ansi.disable";

	/**
	 * IF true will provide additional output on errors.
	 */
	static final String GLOBAL_VERBOSE = ROOT_PREFIX + "global.verbose";

	/**
	 * Logging change properties prefix.
	 */
	static final String CHANGE_PREFIX = ROOT_PREFIX + "change";

	/**
	 * Logging file property for default single file appending. The value should be a URI.
	 */
	static final String FILE_PROPERTY = ROOT_PREFIX + "file.name";

	/**
	 * A common key parameter is called name.
	 */
	static final String NAME = "name";

	/**
	 * For properties keys that are parameterized with {@value #NAME} the name often used
	 * if not specified is: {@value #DEFAULT_NAME}.
	 */
	static final String DEFAULT_NAME = "default";

	/**
	 * Logging output prefix for configuration.
	 */
	static final String OUTPUT_PREFIX = ROOT_PREFIX + "output.{" + NAME + "}.";

	/**
	 * Logging output prefix for configuration.
	 */
	static final String ENCODER_PREFIX = ROOT_PREFIX + "encoder.{" + NAME + "}.";

	/**
	 * Enabled Logging appenders. The value should be a list of names. This is equivalent
	 * to {@value #ROUTE_APPENDERS_PROPERTY} with name "default".
	 */
	static final String APPENDERS_PROPERTY = ROOT_PREFIX + "appenders";

	/**
	 * Logging appender prefix for configuration.
	 */
	static final String APPENDER_PREFIX = ROOT_PREFIX + "appender.{" + NAME + "}.";

	/**
	 * Output appender property.
	 */
	static final String APPENDER_OUTPUT_PROPERTY = LogProperties.APPENDER_PREFIX + "output";

	/**
	 * Encoder appender property.
	 */
	static final String APPENDER_ENCODER_PROPERTY = LogProperties.APPENDER_PREFIX + "encoder";

	/**
	 * Logging publisher prefix for configuration.
	 */
	static final String PUBLISHER_PREFIX = ROOT_PREFIX + "publisher.{" + NAME + "}.";

	/**
	 * Routes enabled with names comma separated.
	 */
	static final String ROUTES_PROPERTY = ROOT_PREFIX + "routes";

	/**
	 * Route prefix for configuration.
	 */
	static final String ROUTE_PREFIX = ROOT_PREFIX + "route.{" + NAME + "}.";

	/**
	 * Logging publisher URI property.
	 */
	static final String ROUTE_PUBLISHER_PROPERTY = ROUTE_PREFIX + "publisher";

	/**
	 * Appenders associated with a route comma separated.
	 */
	static final String ROUTE_APPENDERS_PROPERTY = ROUTE_PREFIX + "appenders";

	/**
	 * Logging level properties prefix. The value should be the name of a
	 * {@linkplain java.lang.System.Logger.Level level}.
	 */
	static final String ROUTE_LEVEL_PREFIX = ROUTE_PREFIX + "level";

	/**
	 * Route flags.
	 */
	static final String ROUTE_FLAGS_PROPERTY = ROUTE_PREFIX + "flags";

	/**
	 * Analogous to {@link System#getProperty(String)}.
	 * @param key property name.
	 * @return property value.
	 */
	public @Nullable String valueOrNull(String key);

	/**
	 * Gets a list or null if the key is missing and by default uses
	 * {@link #parseList(String)}. The default implementation will parse the string value
	 * as a list using {@link LogProperties#parseList(String)} but custom implementations
	 * may rely on their own parsing or the data type is built-in to the backing
	 * configuration system.
	 * @param key property name.
	 * @return list or <code>null</code>.
	 * @apiNote the reason empty list is not returned for missing key is that it creates
	 * ambiguity so null is returned when key is missing.
	 */
	default @Nullable List<String> listOrNull(String key) {
		String s = valueOrNull(key);
		if (s == null) {
			return null;
		}
		return parseList(s);
	}

	/**
	 * Gets a map or null if the key is missing and by default uses
	 * {@link #parseMap(String)}. The default implementation will parse the string value
	 * as map using {@link LogProperties#parseMap(String)} but custom implementations may
	 * rely on their own parsing or the data type is built-in to the backing configuration
	 * system.
	 * @param key property name.
	 * @return map or <code>null</code>.
	 * @apiNote the reason empty map is not returned for missing key is that it creates
	 * ambiguity so null is returned when key is missing.
	 */
	default @Nullable Map<String, String> mapOrNull(String key) {
		String s = valueOrNull(key);
		if (s == null) {
			return null;
		}
		return parseMap(s);
	}

	/**
	 * Find <strong>string</strong> property or <code>null</code>.
	 * @param key property name.
	 * @return found property or <code>null</code>
	 */
	@SuppressWarnings("exports")
	default FoundProperty.@Nullable StringProperty stringPropertyOrNull(String key) {
		String v = valueOrNull(key);
		if (v != null) {
			return new FoundProperty.StringProperty(this, key, v);
		}
		return null;
	}

	/**
	 * Find <strong>list</strong> property or <code>null</code>.
	 * @param key property name.
	 * @return found property or <code>null</code>
	 */
	@SuppressWarnings("exports")
	default FoundProperty.@Nullable ListProperty listPropertyOrNull(String key) {
		var v = listOrNull(key);
		if (v != null) {
			return new FoundProperty.ListProperty(this, key, v);
		}
		return null;
	}

	/**
	 * Find <strong>map</strong> property or <code>null</code>.
	 * @param key property name.
	 * @return found property or <code>null</code>
	 */
	@SuppressWarnings("exports")
	default FoundProperty.@Nullable MapProperty mapPropertyOrNull(String key) {
		var v = mapOrNull(key);
		if (v != null) {
			return new FoundProperty.MapProperty(this, key, v);
		}
		return null;
	}

	/**
	 * Searches up a {@value #SEP} separated path using this properties to check for
	 * values. The first value not missing (not <code>null</code>) will be returned.
	 * @param <T> result type
	 * @param root prefix.
	 * @param key should start with prefix.
	 * @param func convert function where <code>null</code> return means to keep
	 * searching.
	 * @return closest value or <code>null</code> if no value can be found.
	 * @see #findUpPathOrNull(String, Function)
	 */
	@SuppressWarnings("exports")
	default <T extends @Nullable Object> @Nullable T findOrNull(String root, String key,
			BiFunction<LogProperties, String, @Nullable T> func) {
		return findUpPathOrNull(key, k -> func.apply(this, concatKey(root, k)));
	}

	/**
	 * Describes a key and by default is usually just the key.
	 * @param key key.
	 * @return usually the key.
	 */
	default String description(String key) {
		return key;
	}

	/**
	 * When log properties are coalesced this method is used to resolve order. A higher
	 * number gives higher precedence.
	 * @return zero by default.
	 */
	default int order() {
		return 0;
	}

	/**
	 * Found property retrieved from {@link LogProperties}. This is a bridge and meta data
	 * needed for the {@link LogProperty} fluent like monads. It includes the original
	 * value before conversions.
	 *
	 * @apiNote This sealed class is purposely not generic parameterized but you are
	 * allowed to pattern match as the subclasses represent the builtin types of
	 * properties that are supported.
	 */
	public sealed interface FoundProperty {

		/**
		 * The originating <em>exact</em> properties that the value was found on.
		 * @return properties.
		 */
		LogProperties properties();

		/**
		 * The key that was used to find this property.
		 * @return key also known as property name.
		 */
		String key();

		/**
		 * A string representation of the value that this property has usually for error
		 * descriptions.
		 * @return description of value.
		 */
		String valueDescription();

		/**
		 * A found <strong>string</strong> property result which includes the
		 * <strong>exact</strong> properties where a value was found.
		 *
		 * @param properties the <strong>exact</strong> properties where the value was
		 * found.
		 * @param key property key.
		 * @param value property string value.
		 */
		public record StringProperty(LogProperties properties, String key, String value) implements FoundProperty {
			@Override
			public String valueDescription() {
				return maybeRedact(value);
			}

			private static final Set<String> REDACTED_KEYS = Set.of("password", "apikey", "secret", "token");

			private static final String REDACTED_VALUE = "<REDACTED>";

			private static final String maybeRedact(String input) {
				String lower = input.toLowerCase(Locale.ROOT);
				if (REDACTED_KEYS.contains(lower)) {
					return REDACTED_VALUE;
				}
				for (var k : REDACTED_KEYS) {
					if (input.contains(k)) {
						return REDACTED_VALUE;
					}
				}
				return input;
			}
		}

		/**
		 * A found <strong>list</strong> property result which includes the
		 * <strong>exact</strong> properties where a value was found.
		 *
		 * @param properties the <strong>exact</strong> properties where the value was
		 * found.
		 * @param key property key.
		 * @param value property string value.
		 */
		public record ListProperty(LogProperties properties, String key, List<String> value) implements FoundProperty {
			@Override
			public String valueDescription() {
				return StringProperty.maybeRedact("" + value);
			}
		}

		/**
		 * A found <strong>map</strong> property result which includes the
		 * <strong>exact</strong> properties where a value was found.
		 *
		 * @param properties the <strong>exact</strong> properties where the value was
		 * found.
		 * @param key property key.
		 * @param value property string value.
		 */
		public record MapProperty(LogProperties properties, String key,
				Map<String, String> value) implements FoundProperty {
			@Override
			public String valueDescription() {
				return StringProperty.maybeRedact("" + value);
			}
		}

	}

	/**
	 * Abstract log properties builder.
	 *
	 * @param <T> builder.
	 * @apiNote this abstract class can largely be ignored hence why it is sealed.
	 */
	public sealed abstract class AbstractBuilder<T> {

		/**
		 * description
		 */
		protected @Nullable String description = null;

		/**
		 * order
		 */
		protected int order = 0;

		/**
		 * rename key
		 */
		protected Function<String, String> renameKey = s -> s;

		/**
		 * fallbacks
		 */
		protected List<LogProperties> fallbacks = new ArrayList<>();

		/**
		 * Ignored.
		 */
		protected AbstractBuilder() {
		}

		/**
		 * Add a fallback properties.
		 * @param properties fallback not null.
		 * @return this.
		 */
		public T with(LogProperties properties) {
			this.fallbacks.add(properties);
			return self();
		}

		/**
		 * For returning this.
		 * @return this builder.
		 */
		protected abstract T self();

		/**
		 * Description for properties.
		 * @param description not null.
		 * @return this.
		 */
		public T description(String description) {
			this.description = description;
			return self();
		}

		/**
		 * When log properties are coalesced this method is used to resolve order. A
		 * higher number gives higher precedence.
		 * @param order order.
		 * @return this.
		 */
		public T order(int order) {
			this.order = order;
			return self();
		}

		/**
		 * Renames the key before it accesses function.
		 * @param renameKey rename key function.
		 * @return this.
		 */
		public T renameKey(Function<String, String> renameKey) {
			this.renameKey = renameKey;
			return self();
		}

		/**
		 * Removes the prefix from a key before it accesses the underlying properties.
		 * @param prefix to be removed from start of key.
		 * @return this.
		 */
		public T removeKeyPrefix(String prefix) {
			return renameKey(k -> LogProperties.removeKeyPrefix(k, prefix));
		}

	}

	/**
	 * Builder for properties.
	 */
	public final static class Builder extends AbstractBuilder<Builder> {

		private @Nullable Function<Builder, LogProperties> provider = null;

		private Builder() {
		}

		@Override
		protected Builder self() {
			return this;
		}

		private Builder provider(Function<Builder, LogProperties> provider) {
			if (this.provider != null) {
				throw new IllegalArgumentException("from already set");
			}
			this.provider = provider;
			return this;
		}

		/**
		 * Sets what is called on {@link LogProperties#valueOrNull(String)}.
		 * @param function valueOrNull func.
		 * @return this.
		 */
		public Builder fromFunction(@SuppressWarnings("exports") Function<String, @Nullable String> function) {
			return provider((b) -> new DefaultLogProperties(function,
					Objects.requireNonNullElse(description, "function"), renameKey, order));
		}

		/**
		 * Parses a string as {@link Properties}.
		 * @param properties properties as a string.
		 * @return this.
		 */
		public Builder fromProperties(String properties) {
			if (description == null) {
				description = "PROPERTIES_STRING";
			}
			return fromFunction(PropertiesParser.readProperties(properties)::get);
		}

		/**
		 * Parses a string as a URI query string. If the URI has no query portion then it
		 * will be equivalent to empty properties.
		 * @param uri percent encoded uri with separator as "<code>&amp;</code>" and key
		 * value separator of "<code>=</code>".
		 * @return this.
		 */
		public Builder fromURIQuery(URI uri) {
			String query = uri.getRawQuery();
			return provider(
					(b) -> queryToProperties(query, Objects.requireNonNullElse(description, "URI_QUERY(" + uri + ")")));
		}

		/**
		 * Parses a string as a URI query string.
		 * @param query uri percent encoded uri with separator as "<code>&amp;</code>" and
		 * key value separator of "<code>=</code>".
		 * @return this.
		 */
		public Builder fromURIQuery(String query) {
			return provider((b) -> queryToProperties(query,
					Objects.requireNonNullElse(description, "URI_QUERY(" + query + ")")));

		}

		private MultiMapProperties queryToProperties(@Nullable String query, String description) {
			Map<String, List<String>> multiMap;
			if (query == null) {
				multiMap = Map.of();
			}
			else {
				multiMap = parseMultiMap(query);
			}
			var properties = new MultiMapProperties(multiMap, description, renameKey, order);
			return properties;
		}

		/**
		 * Builds LogProperties based on builder config.
		 * @return this.
		 */
		public LogProperties build() {
			var f = this.provider;
			if (f == null) {
				if (!fallbacks.isEmpty()) {
					return LogProperties.of(fallbacks);
				}
				throw new IllegalStateException("from was not set");
			}

			var first = f.apply(this);
			if (fallbacks.isEmpty()) {
				return first;
			}
			List<LogProperties> combined = new ArrayList<>();
			combined.add(first);
			combined.addAll(fallbacks);
			return LogProperties.of(combined);
		}

		static abstract class AbstractLogProperties implements LogProperties {

			protected final String description;

			protected final Function<String, String> renameKey;

			protected final int order;

			public AbstractLogProperties(String description, Function<String, String> renameKey, int order) {
				super();
				this.description = description;
				this.renameKey = renameKey;
				this.order = order;
			}

			@Override
			public int order() {
				return this.order;
			}

			@Override
			public String description(String key) {
				String rename = renameKey.apply(key);
				return description + "[" + rename + "]";
			}

			@Override
			public String toString() {
				return getClass().getSimpleName() + "[description='" + description + "', " + "order=" + order + "]";
			}

		}

		private static final class DefaultLogProperties extends AbstractLogProperties {

			private final Function<String, @Nullable String> func;

			public DefaultLogProperties(Function<String, @Nullable String> func, String description,
					Function<String, String> renameKey, int order) {
				super(description, renameKey, order);
				this.func = func;
			}

			@Override
			public @Nullable String valueOrNull(String key) {
				return func.apply(renameKey.apply(key));
			}

		}

	}

	/**
	 * A log properties that can be mutated like a map.
	 */
	public interface MutableLogProperties extends LogProperties {

		/**
		 * Assigns key to a value.
		 * @param key key.
		 * @param value value if null will remove the key.
		 * @return this.
		 */
		MutableLogProperties put(String key, @Nullable String value);

		/**
		 * Copy java.util {@link Properties} String useful for unit testing.
		 * @param content string in {@link Properties} format (tip use multiline strings).
		 * @return this.
		 */
		default MutableLogProperties copyProperties(String content) {
			var m = PropertiesParser.readProperties(content);
			for (var e : m.entrySet()) {
				put(e.getKey(), e.getKey());
			}
			return this;
		}

		/**
		 * Builder for MutableLogProperties.
		 * @return builder.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Mutable Log properties builder.
		 */
		public final class Builder extends AbstractBuilder<Builder> {

			private Map<String, String> map = new LinkedHashMap<>();

			private Builder() {
			}

			@Override
			protected Builder self() {
				return this;
			}

			/**
			 * Sets the backing mutable map.
			 * @param map backing map.
			 * @return this.
			 */
			public Builder with(Map<String, String> map) {
				this.map = map;
				return this;
			}

			/**
			 * Parses a string as {@link Properties} useful for unit testing with Java
			 * multiline string literal.
			 * @param properties properties as a string.
			 * @return this.
			 */
			public Builder copyProperties(String properties) {
				map.putAll(PropertiesParser.readProperties(properties));
				return this;
			}

			/**
			 * Builds a mutable log properties.
			 * @return mutable log properties.
			 */
			public MutableLogProperties build() {
				String description = this.description;
				if (description == null) {
					description = "custom mutable";
				}
				var first = new MapLogProperties(map, description, renameKey, order);
				if (fallbacks.isEmpty()) {
					return first;
				}
				List<LogProperties> combined = new ArrayList<>();
				combined.add(first);
				combined.addAll(fallbacks);
				return new CompositeMutableLogProperties(sort(combined));
			}

			private static final class MapLogProperties extends LogProperties.Builder.AbstractLogProperties
					implements MutableLogProperties {

				private final Map<String, String> map;

				public MapLogProperties(Map<String, String> map, String description, Function<String, String> renameKey,
						int order) {
					super(description, renameKey, order);
					this.map = map;
				}

				@Override
				public @Nullable String valueOrNull(String key) {
					return map.get(renameKey.apply(key));
				}

				@Override
				public MutableLogProperties put(String key, @Nullable String value) {
					Objects.requireNonNull(key);
					key = renameKey.apply(key);
					if (value == null) {
						map.remove(key);
						return this;
					}
					map.put(key, value);
					return this;
				}

			}

		}

	}

	/**
	 * LogProperties builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Finds global properties by checking the currently bound rainbow gum and if not set
	 * use system properties. <strong>This method should be used sparingly and is
	 * preferred that you get the properties through a config instance instead.</strong>
	 * @return currently bound properties or system properties.
	 * @apiNote the intention of this method is to be used for ServiceLoader singleton
	 * components and other systems that maybe bound prior to rainbow gum loading that
	 * need to determine if they should be enabled.
	 */
	public static LogProperties findGlobalProperties() {
		return findGlobalProperties(() -> StandardProperties.SYSTEM_PROPERTIES);
	}

	/**
	 * Finds global properties by checking the currently bound rainbow gum and if not set
	 * use the supplier as the fallback. <strong>This method should be used sparingly and
	 * is preferred that you get the properties through a config instance
	 * instead.</strong>
	 * @param fallback supplier to use if no rainbow gum is bound.
	 * @return currently bound properties or fallback.
	 * @apiNote the intention of this method is to be used for ServiceLoader singleton
	 * components and other systems that maybe bound prior to rainbow gum loading that
	 * need to determine if they should be enabled.
	 */
	public static LogProperties findGlobalProperties(Supplier<? extends LogProperties> fallback) {
		var gum = RainbowGum.getOrNull();
		if (gum != null) {
			return gum.config().properties();
		}
		return Objects.requireNonNull(fallback.get());
	}

	/**
	 * Creates log properties from many log properties.
	 * @param logProperties list of properties.
	 * @param fallback if the logProperties list is empty.
	 * @return log properties
	 */
	public static LogProperties of(List<? extends LogProperties> logProperties, LogProperties fallback) {
		if (logProperties.isEmpty()) {
			return fallback;
		}
		if (logProperties.size() == 1) {
			return Objects.requireNonNull(logProperties.get(0));
		}
		var array = sort(logProperties);
		return new CompositeLogProperties(array);
	}

	private static LogProperties[] sort(List<? extends LogProperties> logProperties) {
		@SuppressWarnings("null") // TODO eclipse array issue
		LogProperties[] array = logProperties.stream()
			.filter(p -> p != StandardProperties.EMPTY)
			.toArray(size -> new LogProperties[size]);
		Arrays.sort(array, Comparator.comparingInt(LogProperties::order).reversed());
		return array;
	}

	/**
	 * Creates log properties from many log properties.
	 * @param logProperties the list of properties.
	 * @return if the logProperties is empty {@link StandardProperties#EMPTY} will be
	 * used.
	 */
	public static LogProperties of(List<? extends LogProperties> logProperties) {
		return of(logProperties, StandardProperties.EMPTY);
	}

	/**
	 * Creates log properties from a URI query and provided properties. This is useful for
	 * URI based providers where the URI query is contributing properties for the plugin.
	 * @param uri query will be used to pull properties from using
	 * {@link #parseUriQuery(String, BiConsumer)}.
	 * @param prefix part of the key that will stripped before accessing the URI
	 * properties.
	 * @param properties usually this is the properties coming from {@link LogConfig}.
	 * @return combined properties where properties is tried first than the URI query
	 * parameters.
	 */
	public static LogProperties of(URI uri, String prefix, LogProperties properties) {
		return of(uri, prefix, properties, null);
	}

	/**
	 * Creates log properties from a URI query and provided properties. This is useful for
	 * URI based providers where the URI query is contributing properties for the plugin.
	 * @param uri query will be used to pull properties from using
	 * {@link #parseUriQuery(String, BiConsumer)}.
	 * @param prefix part of the key that will stripped before accessing the URI
	 * properties.
	 * @param properties usually this is the properties coming from {@link LogConfig}.
	 * @param uriKey where the URI came from.
	 * @return combined properties where properties is tried first than the URI query
	 * parameters.
	 */
	public static LogProperties of(URI uri, String prefix, LogProperties properties, @Nullable String uriKey) {
		/*
		 * TODO this should be integrated with the whole providers and registries.
		 */
		String propDesc = uriKey != null ? "[" + uriKey + "]->" : "";
		String description = propDesc + "URI(" + uri + ")";
		var uriProperties = LogProperties.builder()
			.description(description)
			.fromURIQuery(uri)
			.renameKey(s -> LogProperties.removeKeyPrefix(s, prefix))
			.build();
		return LogProperties.of(List.of(properties, uriProperties));
	}

	/**
	 * Parse a {@linkplain URI#getRawQuery() URI query} in <a href=
	 * "https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data">
	 * application/x-www-form-urlencoded </a> format useful for parsing properties values
	 * that have key values embedded in them. <strong>This parser unlike form encoding
	 * uses <code>%20</code> for space as the data is coming from a URI.</strong>
	 * @param query raw query component of URI.
	 * @param consumer accept will be called for each entry and if key (left hand) does
	 * not have a "<code>=</code>" following it the second parameter on the consumer
	 * accept will be passed <code>null</code>.
	 */
	public static void parseUriQuery(String query,
			@SuppressWarnings("exports") BiConsumer<String, @Nullable String> consumer) {
		parseUriQuery(query, true, consumer);
	}

	/**
	 * Parses a URI query formatted string to a Map.
	 * @param query raw query component of URI.
	 * @return decoded key values with last key winning over previous equal keys.
	 * @see #parseUriQuery(String, BiConsumer)
	 */
	public static Map<String, String> parseMap(String query) {
		Map<String, String> m = new LinkedHashMap<>();
		parseUriQuery(query, (k, v) -> {
			if (v != null) {
				m.put(k, v);
			}
		});
		return m;
	}

	/**
	 * Parses a URI query for a multiple value map. The list values of the map maybe empty
	 * if the query parameter does not have any values associated with it which would be
	 * the case if there is a parameter (key) with no "<code>=</code>" following it. For
	 * example the following would have three entries of <code>a,b,c</code> all with empty
	 * list: <pre>
	 * <code>
	 * a&amp;b&amp;c&amp;
	 * </code> </pre>
	 * @param query raw query component of URI.
	 * @return decoded key values with multiple keys grouped together in order found.
	 * @see #parseUriQuery(String, BiConsumer)
	 */
	public static Map<String, List<String>> parseMultiMap(String query) {
		Map<String, List<String>> m = new LinkedHashMap<>();
		BiConsumer<String, @Nullable String> f = (k, v) -> {
			List<String> list = Objects.requireNonNull(m.computeIfAbsent(k, _k -> new ArrayList<String>()));
			if (v != null) {
				list.add(v);
			}
		};
		parseUriQuery(query, f);
		return m;
	}

	/**
	 * Parses a list of strings from a string that is <a href=
	 * "https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data">
	 * percent encoded for escaping (application/x-www-form-urlencoded)</a> where the
	 * separator can be either "<code>&amp;</code>" or "<code>,</code>".
	 * <p>
	 * An example would be using ampersand: <pre><code>
	 *  "a&amp;b%20B&amp;c" -> ["a","b B","c"]
	 *  </code> </pre> and comma: <pre><code>
	 *  "a,b,c" -> ["a","b","c"]
	 *  </code> </pre>
	 * @param query the comma or ampersand delimited string that is in URI query format.
	 * @return list of strings
	 */
	public static List<String> parseList(String query) {
		List<String> list = new ArrayList<>();
		parseUriQuery(query, (k, v) -> list.add(k));
		return list;
	}

	private static void parseUriQuery(String query, boolean decode, BiConsumer<String, @Nullable String> consumer) {
		parseUriQuery(query, decode, "[&,]", consumer);
	}

	private static void parseUriQuery(String query, boolean decode, String sep,
			BiConsumer<String, @Nullable String> consumer) {
		/*
		 * TODO default java split has issues but is very fast
		 */
		@SuppressWarnings("StringSplitter")
		String[] pairs = query.split(sep);
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			String key;
			String value;
			if (idx == 0) {
				continue;
			}
			else if (idx < 0) {
				key = pair;
				value = null;
			}
			else {
				key = pair.substring(0, idx);
				value = pair.substring(idx + 1);
			}
			if (decode) {
				key = PercentCodec.decode(key, StandardCharsets.UTF_8);
				if (value != null) {
					value = PercentCodec.decode(value, StandardCharsets.UTF_8);
				}

			}
			if (key.isBlank()) {
				continue;
			}
			consumer.accept(key, value);
		}
	}

	/**
	 * Common static log properties such as System properties ane environment variables.
	 */
	@CaseChanging
	enum StandardProperties implements LogProperties {

		/**
		 * Empty properties. The order is <code>-1</code>.
		 */
		EMPTY {
			@Override
			public @Nullable String valueOrNull(String key) {
				return null;
			}

			@Override
			public int order() {
				return -1;
			}
		},
		/**
		 * Uses {@link System#getProperty(String)} for {@link #valueOrNull(String)}. The
		 * {@link #order()} is <code>400</code> which the same value as MicroProfile
		 * config ordinal.
		 */
		SYSTEM_PROPERTIES {
			@Override
			public @Nullable String valueOrNull(String key) {
				return System.getProperty(key);
			}

			@Override
			public int order() {
				return 400;
			}
		},
		/**
		 * Uses {@link System#getenv(String)} for {@link #valueOrNull(String)}.The
		 * {@link #order()} is <code>300</code> which is the same value as MicroProfile
		 * config ordinal.
		 */
		ENVIRONMENT_VARIABLES {
			@Override
			public @Nullable String valueOrNull(String key) {
				return System.getenv(translateKey(key));
			}

			@Override
			protected String translateKey(String key) {
				return key.replace(".", "_");
			}

		};

		@Override
		public String description(String key) {
			String k = translateKey(key);
			return name() + "[" + k + "]";
		}

		/**
		 * Translate the key.
		 * @param key input
		 * @return output
		 */
		protected String translateKey(String key) {
			return key;
		}

	}

	/**
	 * Searches a {@value LogProperties#SEP} separated property path recursing up the path
	 * till a non null value (not missing) is found.
	 * <p>
	 * For example assume we have a single property set like
	 * <code>logging.level.com.stuff=DEBUG</code> and we call this function with
	 * <code>logging.level.com.stuff.a.b</code>.
	 * <ol>
	 * <li><code>logging.level.com.stuff.a.b</code> will resolve to <code>null</code></li>
	 * <li><code>logging.level.com.stuff.a</code> will resolve to <code>null</code></li>
	 * <li><code>logging.level.com.stuff</code> will resolve to <code>"DEBUG"</code>, stop
	 * and return <code>"DEBUG"</code>.</li>
	 * </ol>
	 * @param <T> type to return.
	 * @param key the initial path.
	 * @param resolveFunc function that returns the value at path or <code>null</code>.
	 * @return value or <code>null</code> if no non null value can be found.
	 */
	@SuppressWarnings("exports")
	public static <T extends @Nullable Object> @Nullable T findUpPathOrNull(String key,
			Function<String, @Nullable T> resolveFunc) {
		return searchPath(key, resolveFunc, SEP);
	}

	private static <T> @Nullable T searchPath(String key, Function<String, @Nullable T> resolveFunc, String sep) {
		String tempName = key;
		@Nullable
		T level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = resolveFunc.apply(tempName);
			indexOfLastDot = tempName.lastIndexOf(sep);
		}
		if (level != null) {
			return level;
		}
		return resolveFunc.apply("");
	}

	/**
	 * Concats a key using {@link #SEP} to the root prefix.
	 * @param name key.
	 * @return concat key.
	 */
	static String concatKey(String name) {
		return concatKey(LogProperties.ROOT_PREFIX, name);
	}

	/**
	 * Concats a keys using {@link #SEP} as the separator.
	 * @param prefix start of key.
	 * @param name second part of key.
	 * @return key.
	 */
	static String concatKey(String prefix, String name) {
		if (name.equals("")) {
			return prefix;
		}
		if (prefix.equals("")) {
			return name;
		}
		if (prefix.endsWith(".")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		if (name.startsWith(SEP)) {
			return prefix + name;
		}
		return prefix + SEP + name;
	}

	/**
	 * Replace "{name}" tokens in property names.
	 * @param key property name.
	 * @param parameters tokens to replace with entry values.
	 * @return interpolated property key.
	 * @see #interpolateKey(String, Function)
	 */
	static String interpolateKey(String key, Map<String, String> parameters) {
		return interpolateKey(key, parameters::get);
	}

	/**
	 * Replaces property <strong>key</strong> parameters by using the lookup function.
	 * Property keys are often parameterized usually by some name. The parameters are
	 * surrounded by curly braces and replaced by the lookup function. An example of the
	 * format is <code>a.{PARAM}</code> which has a parameter named <code>PARAM</code>. If
	 * the lookup function has <code>PARAM->b</code> then the result will be
	 * <code>a.b</code>. If a value for a parameter is null (indicates not found) an
	 * exception is thrown.
	 * @param key to be interpolated.
	 * @param lookup function that may return null for a parameter.
	 * @return interpolated key with all parameters replaced.
	 * @throws IllegalArgumentException if the lookup function does not have a value for a
	 * parameter.
	 */
	public static String interpolateKey(String key,
			@SuppressWarnings("exports") Function<String, ? extends @Nullable String> lookup)
			throws IllegalArgumentException {
		var parameters = keyParameters(key);
		String result = key;
		for (String k : parameters) {
			var v = lookup.apply(k);
			if (v == null) {
				throw new IllegalArgumentException(
						"Keyed parameter missing. key: '" + key + "' parameter: '" + k + "'");
			}
			result = result.replace("{" + k + "}", v);
		}
		return result;
	}

	/**
	 * Interpolates a named key which is a property key that has a {@value #NAME}.
	 * @param key with name parameters.
	 * @param name replace name token.
	 * @return interpolated property key.
	 */
	static String interpolateNamedKey(String key, String name) {
		return interpolateKey(key, Map.of(NAME, name));
	}

	/**
	 * Remove key prefix from the key. Prefix should end in "." but this method does not
	 * check that.
	 * @param key key whose prefix will be removed.
	 * @param prefix property name prefix.
	 * @return prefix removed if the string is prefixed.
	 */
	static String removeKeyPrefix(String key, String prefix) {
		if (key.startsWith(prefix)) {
			return key.substring(prefix.length());
		}
		return key;
	}

	/**
	 * Property prefix parameter pattern.
	 */
	static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{(.*?)\\}");

	/**
	 * Extract key parameters with {@link #PARAMETER_PATTERN}.
	 * @param key also known as property name.
	 * @return parameters in key.
	 */
	static Set<String> keyParameters(String key) {
		Set<String> tokens = new LinkedHashSet<>();
		Matcher matcher = PARAMETER_PATTERN.matcher(key);
		while (matcher.find()) {
			var t = matcher.group(1);
			if (t == null) {
				throw new IllegalStateException("bug with parameter pattern regex.");
			}
			tokens.add(t);
		}
		return tokens;
	}

	/**
	 * Validate key parameters.
	 * @param key key to be interpolated with parameters.
	 * @param parameters names of parameters which must <strong>equal</strong> the set of
	 * the found parameters. while the order obviously does not matter the set cannot have
	 * more parameters than found in the key.
	 */
	static void validateKeyParameters(String key, Set<String> parameters) {
		var keyParameters = keyParameters(key);
		if (!parameters.equals(keyParameters)) {
			throw new IllegalArgumentException("Keyed parameter mismatch. key: '" + key + "' , key parameters: "
					+ keyParameters + " provided parameters: " + parameters);
		}
	}

}

final class MultiMapProperties extends AbstractLogProperties {

	private final Map<String, List<String>> multiMap;

	public MultiMapProperties(Map<String, List<String>> multiMap, String description,
			Function<String, String> renameKey, int order) {
		super(description, renameKey, order);
		this.multiMap = multiMap;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		return _valueOrNull(renameKey.apply(key));
	}

	private @Nullable String _valueOrNull(String key) {
		var list = multiMap.get(key);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(0);
	}

	@Override
	public @Nullable List<String> listOrNull(String key) {
		return multiMap.get(renameKey.apply(key));
	}

	@Override
	public @Nullable Map<String, String> mapOrNull(String key) {
		/*
		 * Now we check for dotted notation since key was not set. key.subkey=value
		 */
		String prefix = renameKey.apply(key) + LogProperties.SEP;
		LinkedHashMap<String, String> m = new LinkedHashMap<>();
		for (var k : multiMap.keySet()) {
			if (k.startsWith(prefix) && !k.equals(prefix)) {
				String v = _valueOrNull(k);
				if (v != null) {
					String _k = k.substring(prefix.length());
					m.put(_k, v);
				}
			}
		}
		return m;
	}

}

interface ListLogProperties extends LogProperties {

	@Override
	default @Nullable String valueOrNull(String key) {
		for (var props : properties()) {
			var value = props.valueOrNull(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	default @Nullable StringProperty stringPropertyOrNull(String key) {
		for (var props : properties()) {
			var value = props.stringPropertyOrNull(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	default @Nullable ListProperty listPropertyOrNull(String key) {
		for (var props : properties()) {
			var value = props.listPropertyOrNull(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	default @Nullable MapProperty mapPropertyOrNull(String key) {
		for (var props : properties()) {
			var value = props.mapPropertyOrNull(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	default String description(String key) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var p : properties()) {
			if (first) {
				first = false;
			}
			else {
				sb.append(", ");
			}
			sb.append(p.description(key));
		}
		return sb.toString();
	}

	LogProperties[] properties();

	default StringBuilder toString(StringBuilder sb) {
		sb.append(getClass().getSimpleName());
		sb.append("[properties=").append(Arrays.deepToString(properties())).append("]");
		return sb;
	}

}

record CompositeMutableLogProperties(LogProperties[] properties) implements ListLogProperties, MutableLogProperties {

	@Override
	public MutableLogProperties put(String key, @Nullable String value) {
		for (var p : properties()) {
			if (p instanceof MutableLogProperties mp && mp != this) {
				mp.put(key, value);
				return this;
			}
		}
		return this;
	}

	@Override
	public String toString() {
		return toString(new StringBuilder()).toString();
	}

}

record CompositeLogProperties(LogProperties[] properties) implements ListLogProperties {
	@Override
	public String toString() {
		return toString(new StringBuilder()).toString();
	}
}
