package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
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
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.LogProperties.PropertyFunction;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.ChildPropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.RequiredPropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.RootPropertyGetter;
import io.jstach.rainbowgum.LogProperties.RequiredResult;
import io.jstach.rainbowgum.LogProperties.Result;
import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/**
 * Provides String based properties like {@link System#getProperty(String)} for default
 * configuration of logging levels and output. Since Rainbowgum is configuration agnostic
 * LogProperties is a simple string key and string value interface to allow almost any
 * configuration implementation. The only required method to implement is
 * {@link #valueOrNull(String)} and thus LogProperties is a functional like interface but
 * using {@link LogProperties#builder()} is preferred so that a
 * {@linkplain #description(String) description} can be added.
 * <p>
 * If a custom {@link LogProperties} is not configured the default RainbowGum uses System
 * properties.
 * <p>
 * The builtin properties to configure RainbowGum are modeled after <a href=
 * "https://docs.spring.io/spring-boot/docs/3.1.0/reference/html/features.html#features.logging">
 * Spring Boot logging </a>
 * <p>
 * LogProperties should generally be accessed with the {@link PropertyGetter} and
 * {@link Property} like-monads which will allow safe <em>programmatic</em> data
 * conversion of the flat key values and useful error messages to users if the property
 * cannot be mapped or is missing. For more <em>declaritive</em> injection of properties
 * see {@link LogConfigurable} which will generate builders that can use LogProperties.
 * <p>
 * Rainbowgum treates {@value #SEP} in the keys as special separator analogous to
 * Javascript/JSON and various other configuration systems. Furthermore to work with a
 * wide set of configuration systems once a property key has a value it is
 * <strong>RECOMMENDED</strong> that there should not be other property key value with a
 * key that is a prefix of the previously mentioned key. For example allowing two
 * properties <code>logging.example=stuff</code>, <code>logging.example.a=A</code> is not
 * recommended as <code>logging.example</code> is defined as a leaf. The exception to this
 * rule is logging levels mapped to logger names for consistency with Spring Boot.
 * <p>
 * A convention used through out Rainbowgum is to have the property names as constants as
 * it makes documentation easier. These constants name are suffixed with
 * <code>_PROPERTY</code> or <code>_PREFIX</code> the former being a fully qualified key
 * that maybe parameterized and the latter being a partial key ending with
 * "<code>.</code>". The fully qualified keys are like leaves on a tree.
 * <p>
 * Property keys can be paramertized by using
 * {@link LogProperties#interpolateKey(String, Map)} and a general pattern is
 * <code>logging.component.{name}.prop</code> where {@value #NAME} is a parameter. Many
 * property constants use this to allow multiple configuration of components by name.
 *
 * <table class="table">
 * <caption><strong>Builtin Property Patterns</strong></caption>
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
 * builtin {@link LogProperties} is generally static (system properties).</td>
 * </tr>
 * <tr>
 * <td>{@value #ROUTES_PROPERTY} = <code>List&lt;String&gt;</code></td>
 * <td>A comma separated list of routes that should be enabled. If not set
 * {@value LogRouter.Router#DEFAULT_ROUTER_NAME } is the default.</td>
 * </tr>
 * <tr>
 * <td>{@value #ROUTE_APPENDERS_PROPERTY} = <code>List&lt;String&gt;</code></td>
 * <td>A comma separated list of appenders associated with the route. If not set
 * {@value #APPENDERS_PROPERTY} is ued.</td>
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
	 * Logging change properties prefix.
	 */
	static final String GLOBAL_CHANGE_PROPERTY = ROOT_PREFIX + "global.change";

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
	 * Analogous to {@link System#getProperty(String)}.
	 * @param key property name.
	 * @return property value.
	 */
	public @Nullable String valueOrNull(String key);

	/**
	 * Gets a list or null if the key is missing and by default uses
	 * {@link #parseList(String)}. The default implementation will parse the string value
	 * as a list using {@link LogProperties#parseList(String)} but custom implementations
	 * may rely on their own parsing or the data type is builtin to the backing
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
	 * rely on their own parsing or the data type is builtin to the backing configuration
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
	 * Searches up a {@value #SEP} separated path using this properties to check for
	 * values.
	 * @param root prefix.
	 * @param key should start with prefix.
	 * @return closest value.
	 * @see #searchPath(String, Function)
	 */
	default @Nullable String search(String root, String key) {
		return search(root, key, (p, k) -> p.valueOrNull(k));
		// return searchPath(key, k -> valueOrNull(concatKey(root, k)));

	}

	/**
	 * Searches up a {@value #SEP} separated path using this properties to check for
	 * values.
	 * @param <T> result type
	 * @param root prefix.
	 * @param key should start with prefix.
	 * @param func convert function
	 * @return closest value.
	 * @see #searchPath(String, Function)
	 */
	@SuppressWarnings("exports")
	default <T extends @Nullable Object> @Nullable T search(String root, String key,
			BiFunction<LogProperties, String, @Nullable T> func) {
		return searchPath(key, k -> func.apply(this, concatKey(root, k)));
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
	 * Abstract log properties builder.
	 *
	 * @param <T> builder.
	 * @apiNote this abstract class can largely be ignored hence why it is sealed.
	 */
	public sealed abstract class AbstractBuilder<T> {

		protected @Nullable String description = null;

		protected int order = 0;

		protected Function<String, String> renameKey = s -> s;

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
		public T from(LogProperties properties) {
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

		enum Format {

			PROPERTIES() {
				@Override
				Map<String, String> toMap(String content) {
					var m = new LinkedHashMap<String, String>();
					StringReader reader = new StringReader(content);
					try {
						PropertiesParser.readProperties(reader, m::put);
					}
					catch (IOException e) {
						throw new UncheckedIOException(e);
					}
					return m;
				}
			},
			URI_QUERY() {
				@Override
				Map<String, String> toMap(String content) {
					return parseMap(content);
				}
			};

			abstract Map<String, String> toMap(String content);

			Function<String, @Nullable String> parse(String content) {
				return toMap(content)::get;
			}

		}

	}

	/**
	 * Builder for properties.
	 */
	public final static class Builder extends AbstractBuilder<Builder> {

		private @Nullable Function<String, @Nullable String> function = null;

		private Builder() {
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Sets what is called on {@link LogProperties#valueOrNull(String)}.
		 * @param function valueOrNull func.
		 * @return this.
		 */
		public Builder function(@SuppressWarnings("exports") Function<String, @Nullable String> function) {
			this.function = function;
			return this;
		}

		/**
		 * Parses a string as {@link Properties}.
		 * @param properties properties as a string.
		 * @return this.
		 */
		public Builder fromProperties(String properties) {
			function = Format.PROPERTIES.parse(properties);
			if (description == null) {
				description = "Properties String";
			}
			return this;
		}

		/**
		 * Parses a string as a URI query string.
		 * @param query uri percent encoded uri with separator as "<code>&amp;</code>" and
		 * key value separator of "<code>=</code>".
		 * @return this.
		 */
		public Builder fromURIQuery(String query) {
			function = Format.URI_QUERY.parse(query);
			return this;
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
			if (query == null) {
				query = "";
			}
			if (description == null) {
				description = "URI('" + uri + "')";
			}
			function = Format.URI_QUERY.parse(query);
			return this;
		}

		/**
		 * Builds LogProperties based on builder config.
		 * @return this.
		 */
		public LogProperties build() {
			var f = this.function;
			if (f == null) {
				if (!fallbacks.isEmpty()) {
					return LogProperties.of(fallbacks);
				}
				throw new IllegalStateException("function is was not set");
			}
			String description = this.description;
			if (description == null) {
				description = "custom";
			}
			var first = new DefaultLogProperties(f, description, renameKey, order);
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
				String desc = "'" + key + "' from " + description;
				if (!rename.equals(key)) {
					desc += "[" + rename + "]";
				}
				return desc;
			}

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
		 * Copy Java Util {@link Properties} String useful for unit testing.
		 * @param content string in {@link Properties} format (tip use multiline strings).
		 * @return this.
		 */
		default MutableLogProperties copyProperties(String content) {
			var m = AbstractBuilder.Format.PROPERTIES.toMap(content);
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
		 * Mutable Log properties builer.
		 */
		public final class Builder extends AbstractBuilder<Builder> {

			protected Map<String, String> map = new LinkedHashMap<>();

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
				map.putAll(Format.PROPERTIES.toMap(properties));
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
	 * Creates log properties from many log properties.
	 * @param logProperties list of properties.
	 * @param fallback if the logProperties list is empty.
	 * @return log properties
	 */
	public static LogProperties of(List<? extends LogProperties> logProperties, LogProperties fallback) {
		if (logProperties.isEmpty()) {
			return fallback;
		}
		if (logProperties.size() == 0) {
			return logProperties.get(0);
		}
		var array = sort(logProperties);
		return new CompositeLogProperties(array);
	}

	private static LogProperties[] sort(List<? extends LogProperties> logProperties) {
		var array = logProperties.stream()
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
		String query = uri.getRawQuery();
		query = query == null ? "" : query;
		var uriProperties = LogProperties.builder()
			.fromURIQuery(query)
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
	 * Parses a URI query for a multi value map. The list values of the map maybe empty if
	 * the query parameter does not have any values associated with it which would be the
	 * case if there is a parameter (key) with no "<code>=</code>" following it. For
	 * example the following would have three entries of <code>a,b,c</code> all with empty
	 * list: <pre>
	 * <code>
	 * a&amp;b&amp;c&amp;
	 * </code> </pre>
	 * @param query raw query component of URI.
	 * @return decoded key values with multiple keys grouped together in order found.
	 */
	public static Map<String, List<String>> parseMultiMap(String query) {
		Map<String, List<String>> m = new LinkedHashMap<>();
		BiConsumer<String, @Nullable String> f = (k, v) -> {
			var list = m.computeIfAbsent(k, _k -> new ArrayList<String>());
			if (v != null) {
				list.add(v);
			}
		};
		parseUriQuery(query, true, f);
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
		parseUriQuery(query, true, "[&,]", (k, v) -> list.add(k));
		return list;
	}

	private static void parseUriQuery(String query, boolean decode, BiConsumer<String, @Nullable String> consumer) {
		parseUriQuery(query, decode, "&", consumer);
	}

	private static void parseUriQuery(String query, boolean decode, String sep,
			BiConsumer<String, @Nullable String> consumer) {
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
		 * {@link #order()} is <code>400</code> which the same value as microprofile
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
		 * {@link #order()} is <code>300</code> which is the same value as microprofile
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
			String description = "'" + key + "' from " + name();
			if (!k.equals(key)) {
				description += "[" + k + "]";
			}
			return description;
		}

		protected String translateKey(String key) {
			return key;
		}

	}

	/**
	 * An error friendly {@link Function} for converting properties.
	 *
	 * @param <T> input type.
	 * @param <R> output type.
	 * @param <E> error type.
	 */
	public interface PropertyFunction<T extends @Nullable Object, R extends @Nullable Object, E extends Exception>
			extends Function<T, R> {

		@Override
		default R apply(T t) {
			try {
				return _apply(t);
			}
			catch (Exception e) {
				sneakyThrow(e);
				throw new RuntimeException(e);
			}
		}

		/**
		 * Apply that throws error.
		 * @param t input
		 * @return output
		 * @throws E if an error happened in function.
		 */
		public R _apply(T t) throws E;

		@SuppressWarnings("unchecked")
		private static <E extends Throwable> void sneakyThrow(final Throwable x) throws E {
			throw (E) x;
		}

	}

	/**
	 * Parent interface for property exceptions.
	 */
	sealed interface PropertyProblem {

	}

	/**
	 * Thrown if an error happens while converting a property.
	 */
	final static class PropertyConvertException extends RuntimeException implements PropertyProblem {

		private static final long serialVersionUID = -6260241455268426342L;

		/**
		 * Key.
		 */
		private final String key;

		/**
		 * Creates convert exception.
		 * @param key property key.
		 * @param message error message
		 * @param cause maybe null.
		 */
		PropertyConvertException(String key, String message, @Nullable Throwable cause) {
			super(message, cause);
			this.key = key;
		}

		/**
		 * Property key.
		 * @return key.
		 */
		public String key() {
			return this.key;
		}

	}

	/**
	 * Throw if property is missing.
	 */
	final static class PropertyMissingException extends NoSuchElementException implements PropertyProblem {

		private static final long serialVersionUID = 4203076848052565692L;

		/**
		 * Creates a missing propety exception.
		 * @param s error message.
		 */
		PropertyMissingException(String s) {
			super(s);
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
	 * @return value or <code>null</code>.
	 */
	@SuppressWarnings("exports")
	public static <T extends @Nullable Object> @Nullable T searchPath(String key,
			Function<String, @Nullable T> resolveFunc) {
		return searchPath(key, resolveFunc, SEP);
	}

	private static <T> @Nullable T searchPath(String key, Function<String, @Nullable T> resolveFunc, String sep) {
		String tempName = key;
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
	 */
	static String interpolateKey(String key, Map<String, String> parameters) {
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			key = key.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return key;
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

	private static void validateKeyParameters(String key, Set<String> parameters) {
		var keyParameters = keyParameters(key);
		if (!parameters.equals(keyParameters)) {
			throw new IllegalArgumentException("Keyed parameter mismatch. key is parameters: " + keyParameters
					+ " provided parameters: " + parameters);
		}
	}

	/**
	 * The result of a property fetched from properties.
	 *
	 * @param <T> property type.
	 */
	sealed interface Result<T> {

		/**
		 * Gets the value.
		 * @return value or <code>null</code>.
		 */
		public @Nullable T valueOrNull();

		/**
		 * Gets a value if there is if not uses the fallback.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		default @Nullable T valueOrNull(@Nullable T fallback) {
			var v = valueOrNull();
			if (v != null) {
				return v;
			}
			return fallback;
		}

		/**
		 * Gets the value and will fail with {@link NoSuchElementException} if there is no
		 * value.
		 * @return value.
		 * @throws PropertyMissingException if there is no value.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		public T value() throws PropertyMissingException, PropertyConvertException;

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws PropertyMissingException if no property and fallback is
		 * <code>null</code>.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		default T value(@Nullable T fallback) throws PropertyMissingException, PropertyConvertException {
			if (fallback == null) {
				return value();
			}
			return value(() -> fallback);
		}

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws PropertyMissingException if no property and fallback is
		 * <code>null</code>.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
				throws PropertyMissingException, PropertyConvertException;

		/**
		 * Convenience that turns a value into an optional.
		 * @return optional.
		 */
		default Optional<T> optional() {
			return Optional.ofNullable(valueOrNull());
		}

		/**
		 * A property that is present.
		 *
		 * @param <T> property type.
		 * @param value actual value.
		 */
		public record Success<T>(T value) implements RequiredResult<T> {
			/**
			 * Succesfully found property value.
			 * @param value actual value should not be <code>null</code>.
			 */
			public Success {
				if (value == null) {
					throw new NullPointerException("value");
				}
			}

			@Override
			public @Nullable T valueOrNull() {
				return value();
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				return value;
			}
		}

		/**
		 * A property that is missing (<code>null</code>).
		 *
		 * @param <T> property type.
		 * @param message description of where the property is missing.
		 */
		public record Missing<T>(String message) implements Result<T> {
			@Override
			public @Nullable T valueOrNull() {
				return null;
			}

			@Override
			public T value() throws NoSuchElementException {
				throw new PropertyMissingException(message);
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				var v = fallback.get();
				if (v != null) {
					return v;
				}
				throw new PropertyMissingException(message);
			}

			/**
			 * Helper just to cast the result to a different parameterized type.
			 * @param <R> paramterized type.
			 * @return this.
			 */
			@SuppressWarnings("unchecked")
			public <R> Missing<R> convert() {
				return (Missing<R>) this;
			}

		}

		/**
		 * A property that was present but failed conversion.
		 *
		 * @param <T> property type.
		 * @param key property key that failed to convert.
		 * @param message failure message.
		 * @param cause exception thrown while trying to convert.
		 */
		public record Error<T>(String key, String message, Exception cause) implements RequiredResult<T> {

			@Override
			public @Nullable T valueOrNull() {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public T value() throws NoSuchElementException {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				throw new PropertyConvertException(key, message, cause);
			}

			/**
			 * Helper just to cast the result to a different parameterized type.
			 * @param <R> paramterized type.
			 * @return this.
			 */
			@SuppressWarnings("unchecked")
			public <R> Error<R> convert() {
				return (Error<R>) this;
			}
		}

	}

	/**
	 * A result that is not missing and will either be an error or success.
	 *
	 * @param <T> property type.
	 */
	sealed interface RequiredResult<T> extends Result<T> {

	}

	/**
	 * A property description that can retrieve a property result by being passed
	 * {@link LogProperties}. <em>The property instance does not actually contain the
	 * value of the property!</em>.
	 *
	 * @param <T> property type.
	 * @see RequiredProperty
	 * @see Result
	 */
	sealed interface Property<T> {

		/**
		 * Gets the first key.
		 * @return key.
		 */
		public String key();

		/**
		 * Gets a property value as a result.
		 * @param properties key values.
		 * @return result.
		 */
		public Result<T> get(LogProperties properties);

		/**
		 * Maps the property to another value type.
		 * @param <U> value type.
		 * @param mapper function to map.
		 * @return property.
		 */
		public <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper);

		/**
		 * Converts the value into a String that can be parsed by the builtin properties
		 * parsing of types. Supported types: String, Boolean, Integer, URI, Map, List.
		 * @param value to be converted to string.
		 * @return property representation of value.
		 */
		public String propertyString(T value);

		/**
		 * Set a property if its not null.
		 * @param value value to set.
		 * @param consumer first parameter is first key and second parameter is non null
		 * value.
		 */
		public void set(T value, BiConsumer<String, T> consumer);

		/**
		 * Builder.
		 * @return builder.
		 */
		public static RootPropertyGetter builder() {
			return PropertyGetter.of();
		}

	}

	/**
	 * A property that should never be missing usually because there is non-null fallback.
	 *
	 * @param <T> property type.
	 */
	sealed interface RequiredProperty<T> extends Property<T> {

		/**
		 * Gets a property value as a result.
		 * @param properties key values.
		 * @return result.
		 */
		public RequiredResult<T> get(LogProperties properties);

		/**
		 * Maps the property to another value type.
		 * @param <U> value type.
		 * @param mapper function to map.
		 * @return property.
		 */
		public <U> RequiredProperty<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper);

	}

	/**
	 * Extracts and converts from {@link LogProperties} and is also an immutable builder
	 * for {@link Property}s.
	 *
	 * @param <T> value type.
	 */
	sealed interface PropertyGetter<T> {

		/**
		 * Gets a result from properties.
		 * @param props properties.
		 * @param key property key usually dotted.
		 * @return result.
		 */
		Result<T> get(LogProperties props, String key);

		/**
		 * Gets a result from properties by trying a list of keys.
		 * @param props properties.
		 * @param keys list of property keys usually dotted.
		 * @return result.
		 */
		default Result<T> get(LogProperties props, List<String> keys) {
			for (String key : keys) {
				var r = get(props, key);
				@Nullable
				Result<T> found = switch (r) {
					case Result.Success<T> s -> s;
					case Result.Error<T> s -> s;
					case Result.Missing<T> s -> null;
				};
				if (found != null) {
					return found;
				}
			}
			return findRoot(this).missingResult(props, keys);
		}

		/**
		 * Converts the value into a String that can be parsed by the builtin properties
		 * parsing of types. Supported types: String, Boolean, Integer, URI, Map, List.
		 * @param value to be converted to string.
		 * @return property representation of value.
		 */
		String propertyString(T value);

		/**
		 * Determines full name of key.
		 * @param key key.
		 * @return fully qualified key name.
		 */
		default String fullyQualifiedKey(String key) {
			return findRoot(this).fullyQualifiedKey(key);
		}

		/**
		 * Validates the key is correctly prefixed.
		 * @param key dotted key.
		 */
		default void validateKey(String key) {
			String fqn = fullyQualifiedKey(key);
			if (!fqn.startsWith(LogProperties.ROOT_PREFIX)) {
				throw new IllegalArgumentException(
						"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'. key = " + key);
			}
			if (fqn.endsWith(".") || fqn.startsWith(".")) {
				throw new IllegalArgumentException(
						"Property key should not start or end with '" + LogProperties.SEP + "'");
			}
			validateKeyParameters(key, Set.of());
		}

		/**
		 * Creates a Property from the given key and this getter.
		 * @param key key.
		 * @return property.
		 * @throws IllegalArgumentException if the key is malformed.
		 */
		default Property<T> build(String key) throws IllegalArgumentException {
			validateKey(key);
			return new DefaultProperty<>(this, List.of(key));
		}

		/**
		 * Creates a Property from the given key and its {@value LogProperties#NAME}
		 * parameter.
		 * @param key key.
		 * @param name interpolates <code>{name}</code> in property name with this value.
		 * @return property.
		 */
		default Property<T> buildWithName(String key, String name) {
			var parameters = Map.of(NAME, name);
			validateKeyParameters(key, parameters.keySet());
			String fqn = LogProperties.interpolateKey(key, parameters);
			return build(fqn);
		}

		/**
		 * Find root property getter.
		 * @param e property getter.
		 * @return root.
		 */
		public static RootPropertyGetter findRoot(PropertyGetter<?> e) {
			return switch (e) {
				case RootPropertyGetter r -> r;
				case ChildPropertyGetter<?> c -> findRoot(c.parent());
			};
		}

		/**
		 * Default root property getter.
		 * @return property getter.
		 */
		public static RootPropertyGetter of() {
			return new RootPropertyGetter("", false);
		}

		/**
		 * A property getter and <strong>builder</strong> that has no conversion but may
		 * prefix the key and search recursively up the key path.
		 * <p>
		 * While you can map the string properties with {@link #map(PropertyFunction)} it
		 * is preferred to use the <code>to</code> methods on this class for basic types
		 * especially for list or map types as the implementation will call the
		 * implementation specific in LogProperties. An example is {@link #toMap()} which
		 * will call the LogProperties implementration of
		 * {@link LogProperties#mapOrNull(String)}.
		 *
		 * @param prefix added to the key before looking up in {@link LogProperties}.
		 * @param search if true will recursively search up the key path
		 */
		record RootPropertyGetter(String prefix, boolean search) implements PropertyGetter<String> {

			// @Override
			private @Nullable String valueOrNull(LogProperties props, String key) {
				if (search) {
					return props.search(prefix, key);
				}
				return props.valueOrNull(fullyQualifiedKey(key));
			}

			private <T> @Nullable T valueOrNull(LogProperties props, String key,
					BiFunction<LogProperties, String, @Nullable T> func) {
				if (search) {
					return props.search(prefix, key, func);
				}
				return func.apply(props, fullyQualifiedKey(key));
			}

			<T> Result<T> get(LogProperties props, String key, BiFunction<LogProperties, String, @Nullable T> func) {
				var v = valueOrNull(props, key, func);
				if (v == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success<>(v);
			}

			@Override
			public Result<String> get(LogProperties props, String key) {
				var v = valueOrNull(props, key);
				if (v == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success<>(v);
			}

			@Override
			public String propertyString(String value) {
				return value;
			}

			<T> Result.Error<T> errorResult(LogProperties props, String key, Exception e) {
				String resolvedKey = props.description(fullyQualifiedKey(key));
				String message = "Error for property. key: '" + resolvedKey + "', " + e.getMessage();
				return new Result.Error<>(resolvedKey, message, e);
			}

			<T> Result.Missing<T> missingResult(LogProperties props, List<String> keys) {
				List<String> resolvedKeys = describeKeys(props, keys);
				String message = "Property missing. keys: " + resolvedKeys;
				return new Result.Missing<>(message);
			}

			List<String> describeKeys(LogProperties props, List<String> keys) {
				return keys.stream().map(k -> props.description(fullyQualifiedKey(k))).toList();
			}

			/**
			 * Will search with prefix.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withSearch(String prefix) {
				return new RootPropertyGetter(prefix, true);
			}

			/**
			 * Will prefix key.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withPrefix(String prefix) {
				return new RootPropertyGetter(prefix, search);
			}

			@Override
			public String fullyQualifiedKey(String key) {
				return concatKey(prefix, key);
			}

			/**
			 * An integer property getter.
			 * @return getter that will convert to integers.
			 */
			public PropertyGetter<Integer> toInt() {
				return new FuncGetter<>(this, Integer::parseInt, String::valueOf);
			}

			/**
			 * A boolean property getter.
			 * @return getter that will convert to boolean.
			 */
			public PropertyGetter<Boolean> toBoolean() {
				return new FuncGetter<>(this, Boolean::parseBoolean, String::valueOf);
			}

			/**
			 * A Map property that will use {@link LogProperties#mapOrNull(String)}.
			 * @return getter that will convert string property to a Map.
			 */
			public PropertyGetter<Map<String, String>> toMap() {
				return new MapGetter(this);
			}

			/**
			 * A list property that will use {@link LogProperties#listOrNull(String)}.
			 * @return getter that will convert string property to a List.
			 */
			public PropertyGetter<List<String>> toList() {
				return new ListGetter(this);
			}

			/**
			 * An enum property getter using {@link Enum#valueOf(Class, String)}.
			 * @param <T> enum type.
			 * @param enumClass enum class.
			 * @return getter that will convert to enum type.
			 */
			public <T extends Enum<T>> PropertyGetter<T> toEnum(Class<T> enumClass) {
				return new FuncGetter<>(this, s -> Enum.valueOf(enumClass, s), String::valueOf);
			}

			/**
			 * A URI property getter that parses URIs.
			 * @return getter that will parse URIs.
			 */
			public PropertyGetter<URI> toURI() {
				return new FuncGetter<>(this, URI::new, String::valueOf);
			}

		}

		/**
		 * Mapped property getter.
		 *
		 * @param <T> will convert to this type.
		 */
		public sealed interface ChildPropertyGetter<T> extends PropertyGetter<T> {

			/**
			 * Parent.
			 * @return parent.
			 */
			PropertyGetter<?> parent();

			@Override
			default String propertyString(T value) {
				return switch (value) {
					case null -> throw new NullPointerException("null value passed into propertyString");
					case String s -> s;
					case Boolean b -> String.valueOf(b);
					case Integer i -> String.valueOf(i);
					case URI u -> String.valueOf(u);
					case Map<?, ?> m -> MapGetter._propertyString(m);
					case List<?> list -> ListGetter._propertyString(list);
					default -> throw new RuntimeException("Unable to convert to property string. value = " + value);
				};
			}

		}

		/**
		 * Sets up to converts a value.
		 * @param <U> value type
		 * @param mapper function.
		 * @return new property getter.
		 */
		default <U> PropertyGetter<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new FuncGetter<T, U>(this, mapper, null);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback fallback value.
		 * @return new property getter.
		 */
		default RequiredPropertyGetter<T> orElse(T fallback) {
			if (fallback == null) {
				throw new NullPointerException();
			}
			return new FallbackGetter<T>(this, () -> fallback);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback supplier.
		 * @return new property getter.
		 */
		default RequiredPropertyGetter<T> orElseGet(Supplier<? extends T> fallback) {
			if (fallback == null) {
				throw new NullPointerException();
			}
			return new FallbackGetter<T>(this, fallback);
		}

		/**
		 * A property getter that guarantees the result will not be missing usually
		 * because a non-null fallback is provided somewhere.
		 *
		 * @param <T> property type.
		 */
		public sealed interface RequiredPropertyGetter<T> extends ChildPropertyGetter<T> {

			/**
			 * This call unlike the parent returns a required result. {@inheritDoc}
			 */
			RequiredResult<T> get(LogProperties props, String key);

			/**
			 * Gets a result from properties by trying a list of keys.
			 * @param props properties.
			 * @param keys list of property keys usually dotted.
			 * @return result.
			 */
			default RequiredResult<T> get(LogProperties props, List<String> keys) {
				if (keys.isEmpty()) {
					throw new IllegalArgumentException("Keys is empty");
				}
				String key = keys.getFirst();
				return get(props, key);
			}

			/**
			 * Sets up to converts a value.
			 * @param <U> value type
			 * @param mapper function.
			 * @return new property getter.
			 */
			default <U> RequiredPropertyGetter<U> map(
					PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
				return new RequiredFuncGetter<T, U>(this, mapper, null);
			}

			/**
			 * Creates a Property from the given key and this getter.
			 * @param key key.
			 * @return property.
			 * @throws IllegalArgumentException if the key is malformed.
			 */
			default RequiredProperty<T> build(String key) throws IllegalArgumentException {
				validateKey(key);
				return new DefaultRequiredProperty<>(this, List.of(key));
			}

			/**
			 * Creates a Property from the given key and its {@value LogProperties#NAME}
			 * parameter.
			 * @param key key.
			 * @param name interpolates <code>{name}</code> in property name with this
			 * value.
			 * @return property.
			 */
			default RequiredProperty<T> buildWithName(String key, String name) {
				var parameters = Map.of(NAME, name);
				validateKeyParameters(key, parameters.keySet());
				String fqn = LogProperties.interpolateKey(key, parameters);
				return build(fqn);
			}

		}

	}

}

abstract class AbstractLogPropertiesBuilder<T> {

	protected abstract T self();

}

record DefaultProperty<T>(PropertyGetter<T> propertyGetter, List<String> keys) implements LogProperties.Property<T> {

	public DefaultProperty {
		Objects.requireNonNull(propertyGetter);
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("should have at least one key");
		}
	}

	/**
	 * Gets the first key.
	 * @return key.
	 */
	public String key() {
		return keys.get(0);
	}

	@Override
	public Result<T> get(LogProperties properties) {
		return propertyGetter.get(properties, keys);
	}

	public <U> LogProperties.Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		return new DefaultProperty<>(propertyGetter.map(mapper), keys);
	}

	public String propertyString(T value) {
		return propertyGetter.propertyString(value);
	}

	public void set(T value, BiConsumer<String, T> consumer) {
		if (value != null) {
			consumer.accept(key(), value);
		}
	}

}

record DefaultRequiredProperty<T>(RequiredPropertyGetter<T> propertyGetter,
		List<String> keys) implements LogProperties.RequiredProperty<T> {

	public DefaultRequiredProperty {
		Objects.requireNonNull(propertyGetter);
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("should have at least one key");
		}
	}

	/**
	 * Gets the first key.
	 * @return key.
	 */
	public String key() {
		return keys.get(0);
	}

	@Override
	public RequiredResult<T> get(LogProperties properties) {
		return propertyGetter.get(properties, keys);
	}

	public <U> LogProperties.RequiredProperty<U> map(
			PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		return new DefaultRequiredProperty<>(propertyGetter.map(mapper), keys);
	}

	public String propertyString(T value) {
		return propertyGetter.propertyString(value);
	}

	public void set(T value, BiConsumer<String, T> consumer) {
		if (value != null) {
			consumer.accept(key(), value);
		}
	}

}

record MapProperties(String description, Map<String, String> map) implements LogProperties {

	@Override
	public @Nullable String valueOrNull(String key) {
		return map.get(key);
	}

	@Override
	public String description(String key) {
		return description + " (" + key + ")";
	}

}

record FallbackGetter<T>(PropertyGetter<T> parent,
		Supplier<? extends T> fallback) implements RequiredPropertyGetter<T> {

	@Override
	public RequiredResult<T> get(LogProperties props, String key) {
		var r = parent.get(props, key);
		RequiredResult<T> req = switch (r) {
			case Result.Missing<T> m -> {
				var f = fallback.get();
				if (f == null) {
					yield PropertyGetter.findRoot(parent) //
						.errorResult(props, key, new LogProperties.PropertyMissingException("fallback returned null"));
				}
				yield new Result.Success<>(f);
			}
			case Result.Success<T> s -> s;
			case Result.Error<T> e -> e;
		};
		return req;
	}

	@Override
	public String propertyString(T value) {
		return parent.propertyString(value);
	}

}

record MapGetter(RootPropertyGetter parent) implements ChildPropertyGetter<Map<String, String>> {

	@Override
	public Result<Map<String, String>> get(LogProperties props, String key) {
		return parent.get(props, key, (p, k) -> p.mapOrNull(k));
	}

	@Override
	public String propertyString(Map<String, String> value) {
		return _propertyString(value);
	}

	static String _propertyString(Map<?, ?> value) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : value.entrySet()) {
			if (first) {
				first = true;
			}
			else {
				sb.append("&");
			}
			PercentCodec.encode(sb, String.valueOf(e.getKey()), StandardCharsets.UTF_8);
			Object v = e.getValue();
			if (v != null) {
				sb.append("=");
				PercentCodec.encode(sb, String.valueOf(v), StandardCharsets.UTF_8);
			}
		}
		return sb.toString();
	}

}

record ListGetter(RootPropertyGetter parent) implements ChildPropertyGetter<List<String>> {

	@Override
	public Result<List<String>> get(LogProperties props, String key) {
		return parent.get(props, key, (p, k) -> p.listOrNull(k));
	}

	public String propertyString(List<String> list) {
		return _propertyString(list);
	}

	static String _propertyString(List<?> list) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : list) {
			if (first) {
				first = true;
			}
			else {
				sb.append(",");
			}
			PercentCodec.encode(sb, String.valueOf(e), StandardCharsets.UTF_8);
		}
		return sb.toString();
	}

}

record FuncGetter<T, R>(PropertyGetter<T> parent, PropertyFunction<? super T, ? extends R, ? super Exception> mapper,
		@Nullable PropertyFunction<? super R, ? extends String, ? super Exception> stringFunc)
		implements
			ChildPropertyGetter<R> {

	@Override
	public Result<R> get(LogProperties props, String key) {
		var result = parent.get(props, key);
		return switch (result) {
			case Result.Success<T> s -> {
				try {
					R r = mapper._apply(s.value());
					if (r == null) {
						yield PropertyGetter.findRoot(parent).missingResult(props, List.of(key));
					}
					yield new Result.Success<>(r);
				}
				catch (Exception e) {
					yield PropertyGetter.findRoot(parent).errorResult(props, key, e);
				}
			}
			case Result.Error<T> e -> e.convert();
			case Result.Missing<T> m -> m.convert();
		};
	}

	@Override
	public String propertyString(R value) {
		var f = stringFunc;
		if (f != null) {
			return stringFunc.apply(value);
		}
		return ChildPropertyGetter.super.propertyString(value);
	}

}

record RequiredFuncGetter<T, R>(RequiredPropertyGetter<T> parent,
		PropertyFunction<? super T, ? extends R, ? super Exception> mapper,
		@Nullable PropertyFunction<? super R, ? extends String, ? super Exception> stringFunc)
		implements
			RequiredPropertyGetter<R> {

	@Override
	public RequiredResult<R> get(LogProperties props, String key) {
		var result = parent.get(props, key);
		return switch (result) {
			case Result.Success<T> s -> {
				try {
					R r = mapper._apply(s.value());
					if (r == null) {
						throw new NullPointerException(
								"The property function returned a null. props=" + props + ", key=" + key);
					}
					yield new Result.Success<>(r);
				}
				catch (Exception e) {
					yield PropertyGetter.findRoot(parent).errorResult(props, key, e);
				}
			}
			case Result.Error<T> e -> e.convert();
		};
	}

	@Override
	public String propertyString(R value) {
		var f = stringFunc;
		if (f != null) {
			return stringFunc.apply(value);
		}
		return RequiredPropertyGetter.super.propertyString(value);
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
			}
		}
		return this;
	}

	public String toString() {
		return toString(new StringBuilder()).toString();
	}

}

record CompositeLogProperties(LogProperties[] properties) implements ListLogProperties {
	public String toString() {
		return toString(new StringBuilder()).toString();
	}
}
