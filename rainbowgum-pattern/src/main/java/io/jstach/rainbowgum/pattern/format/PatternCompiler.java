package io.jstach.rainbowgum.pattern.format;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.CompositeFactory;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.KeywordFactory;
import io.jstach.rainbowgum.pattern.internal.Node;
import io.jstach.rainbowgum.pattern.internal.Node.CompositeNode;
import io.jstach.rainbowgum.pattern.internal.Node.End;
import io.jstach.rainbowgum.pattern.internal.Node.FormattingNode;
import io.jstach.rainbowgum.pattern.internal.Node.KeywordNode;
import io.jstach.rainbowgum.pattern.internal.Node.LiteralNode;
import io.jstach.rainbowgum.pattern.internal.Parser;
import io.jstach.rainbowgum.pattern.internal.ScanException;

/**
 * Compiles a pattern into a formatter.
 */
public sealed interface PatternCompiler {

	/**
	 * Compiles a pattern into a formatter.
	 * @param pattern logback style pattern.
	 * @return formatter.
	 */
	public LogFormatter compile(String pattern);

	/**
	 * Creates a pattern compiler builder. If nothing is set the default pattern registry
	 * and formatting config will be used.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a pattern compiler provider from a builder lambda.
	 * @param consumer builder will be provided to the consumer.
	 * @return provider of pattern compiler.
	 */
	public static LogProvider<PatternCompiler> of(Consumer<Builder> consumer) {
		return (name, config) -> {
			var services = config.serviceRegistry();
			var registry = findService(services, PatternRegistry.class, name, LogProperties.DEFAULT_NAME)
				.orElseGet(() -> PatternRegistry.of());
			var patternConfig = findService(services, PatternConfig.class, name, LogProperties.DEFAULT_NAME)
				.orElseGet(() -> {
					boolean ansiDisable = LogProperty.Property.builder() //
						.ofBoolean() //
						.build(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY) //
						.get(config.properties()) //
						.value(false);
					var b = PatternConfig.builder().fromProperties(config.properties());
					if (ansiDisable) {
						b.ansiDisabled(true);
					}
					return b.build();
				});
			Builder b = builder();
			b.patternRegistry(registry);
			b.patternConfig(patternConfig);
			consumer.accept(b);
			return b.build();
		};
	}

	private static <T> Optional<T> findService(ServiceRegistry services, Class<T> c, String... names) {
		Stream<String> sn = Stream.of(names);
		return sn.flatMap(s -> Stream.ofNullable(services.findOrNull(c, s))).findFirst();
	}

	/**
	 * Builder for {@link PatternCompiler}.
	 */
	public final static class Builder {

		private PatternRegistry patternRegistry;

		private PatternConfig patternConfig;

		private Builder() {
		}

		/**
		 * Pattern keyword registry.
		 * @param patternRegistry keyword names registry.
		 * @return this.
		 */
		public Builder patternRegistry(PatternRegistry patternRegistry) {
			this.patternRegistry = patternRegistry;
			return this;
		}

		/**
		 * Formatting config that has platform config like time zone etc.
		 * @param patternConfig config for formatting of keywords.
		 * @return this.
		 */
		public Builder patternConfig(PatternConfig patternConfig) {
			this.patternConfig = patternConfig;
			return this;
		}

		/**
		 * Creates a pattern compiler.
		 * @return pattern compiler.
		 */
		public PatternCompiler build() {
			var patternRegistry_ = patternRegistry;
			var formatterConfig_ = patternConfig;
			if (patternRegistry_ == null) {
				patternRegistry_ = PatternRegistry.of();
			}
			if (formatterConfig_ == null) {
				formatterConfig_ = PatternConfig.of();
			}
			return new Compiler(patternRegistry_, formatterConfig_);
		}

	}

}

final class Compiler implements PatternCompiler {

	private final PatternConfig config;

	private final PatternRegistry registry;

	Compiler(PatternRegistry registry, PatternConfig config) {
		this.config = config;
		this.registry = registry;
	}

	@Override
	public LogFormatter compile(String pattern) {
		try {
			Parser p = new Parser(pattern);
			return compile(p.parse());
		}
		catch (ScanException | IllegalStateException e) {
			throw new IllegalArgumentException("Pattern is invalid: " + pattern, e);
		}
	}

	LogFormatter compile(Node start) {
		var b = LogFormatter.builder();
		if (start == Node.end()) {
			return b.build();
		}

		for (Node n = start; n != Node.end();) {
			n = switch (n) {
				case End e -> {
					yield e;
				}
				case LiteralNode ln -> {
					b.text(ln.value());
					yield ln.next();
				}
				case FormattingNode fn -> {
					PatternFormatterFactory f = registry.getOrNull(fn.keyword());
					if (f == null) {
						throw new IllegalStateException("Missing formatter for key: " + fn.keyword());
					}

					var _child = switch (fn) {
						case CompositeNode cn -> cn.childNode();
						case KeywordNode kn -> null;
					};

					var formatter = switch (f) {
						case KeywordFactory kf -> kf.create(config, fn);
						case CompositeFactory cf -> {
							LogFormatter child = _child == null ? null : compile(_child);
							yield cf.create(config, fn, child);
						}
					};
					b.add(formatter);
					yield fn.next();
				}
			};
		}
		return b.build();
	}

}
