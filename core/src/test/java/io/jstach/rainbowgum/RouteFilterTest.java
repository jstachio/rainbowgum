package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

class RouteFilterTest {

	static final LogFormatter FORMATTER = LogFormatter.builder().level().space().message().newline().build();

	/*
	 * Logs one event at every level and returns what the route wrote.
	 */
	private static String log(String properties, Consumer<LogConfig> setup, Consumer<Router.Builder> route) {
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		setup.accept(config);
		try (var gum = RainbowGum.builder(config).route(r -> {
			r.appender("app", a -> a.output(output).formatter(FORMATTER));
			route.accept(r);
		}).build().start()) {
			for (var level : Level.values()) {
				if (level == Level.ALL || level == Level.OFF) {
					continue;
				}
				gum.log(LogEventFactory.of("test").eventNoArg(level, "hello", KeyValues.of(), (Throwable) null));
			}
		}
		return output.toString();
	}

	private static String log(String properties, Consumer<LogConfig> setup) {
		return log(properties, setup, r -> {
		});
	}

	/*
	 * Moves warnings to info and errors to warning, for a dependency that is too loud.
	 */
	static final Filter SLIDE = e -> switch (e.level()) {
		case WARNING -> LogEvent.withLevel(e, Level.INFO);
		case ERROR -> LogEvent.withLevel(e, Level.WARNING);
		default -> e;
	};

	static final Filter MASK_VOWELS = e -> LogEvent.rewrite(e, new LogEvent.Rewriter() {
		@Override
		public void formattedMessage(LogEvent event, StringBuilder sb) {
			int start = sb.length();
			event.formattedMessage(sb);
			for (int i = start; i < sb.length(); i++) {
				if ("aeiou".indexOf(sb.charAt(i)) >= 0) {
					sb.setCharAt(i, '*');
				}
			}
		}
	});

	static void registerSlide(LogConfig config) {
		Filter.register(config.serviceRegistry(), "slide", (name, c) -> SLIDE);
	}

	@Test
	void noFiltersByDefault() {
		String actual = log("logging.level=WARNING", RouteFilterTest::registerSlide);
		assertEquals("""
				WARN hello
				ERROR hello
				""", actual);
	}

	@Test
	void filterSlidesLevelsDown() {
		String actual = log("""
				logging.level=INFO
				logging.route.default.filters=slide
				""", RouteFilterTest::registerSlide);
		assertEquals("""
				INFO hello
				INFO hello
				WARN hello
				""", actual);
	}

	@Test
	void eventSlidToADisabledLevelIsDropped() {
		String actual = log("""
				logging.level=WARNING
				logging.route.default.filters=slide
				""", RouteFilterTest::registerSlide);
		assertEquals("""
				WARN hello
				""", actual);
	}

	@Test
	void filterDropsEvents() {
		String actual = log("""
				logging.level=TRACE
				logging.route.default.filters=debugOnly
				""", c -> Filter.register(c.serviceRegistry(), "debugOnly",
				(name, config) -> e -> e.level() == Level.DEBUG ? e : null));
		assertEquals("""
				DEBUG hello
				""", actual);
	}

	@Test
	void filtersRunInTheOrderListed() {
		Consumer<LogConfig> setup = c -> {
			registerSlide(c);
			Filter.register(c.serviceRegistry(), "warningsOnly",
					(name, config) -> e -> e.level() == Level.WARNING ? e : null);
		};
		String slideFirst = log("""
				logging.level=INFO
				logging.route.default.filters=slide,warningsOnly
				""", setup);
		assertEquals("""
				WARN hello
				""", slideFirst);
		String slideLast = log("""
				logging.level=INFO
				logging.route.default.filters=warningsOnly,slide
				""", setup);
		assertEquals("""
				INFO hello
				""", slideLast);
	}

	@Test
	void builderFiltersRunBeforePropertyFilters() {
		String actual = log("""
				logging.level=WARNING
				logging.route.default.filters=slide
				""", RouteFilterTest::registerSlide, r -> r.filter(MASK_VOWELS));
		assertEquals("""
				WARN h*ll*
				""", actual);
	}

	@Test
	void providerGetsTheRouteNameAndConfig() {
		String actual = log("""
				logging.level=ERROR
				logging.route.default.filters=tag
				app.tag=[%s]
				""", c -> Filter.register(c.serviceRegistry(), "tag", (name, config) -> {
			String tag = config.properties().valueOrNull("app.tag");
			return e -> LogEvent.rewrite(e, new LogEvent.Rewriter() {
				@Override
				public void formattedMessage(LogEvent event, StringBuilder sb) {
					sb.append(String.valueOf(tag).formatted(name)).append(' ');
					event.formattedMessage(sb);
				}
			});
		}));
		assertEquals("""
				ERROR [default] hello
				""", actual);
	}

	@Test
	void otherRoutesGetTheOriginalEvent() {
		var filtered = new ListLogOutput();
		var unfiltered = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.level=WARNING
				logging.route.filtered.filters=slide
				""").build()).build();
		registerSlide(config);
		try (var gum = RainbowGum.builder(config)
			.route("filtered", r -> r.appender("filtered", a -> a.output(filtered).formatter(FORMATTER)))
			.route("unfiltered", r -> r.appender("unfiltered", a -> a.output(unfiltered).formatter(FORMATTER)))
			.build()
			.start()) {
			gum.log(LogEventFactory.of("test").eventNoArg(Level.ERROR, "hello", KeyValues.of(), (Throwable) null));
		}
		assertEquals("WARN hello\n", filtered.toString());
		assertEquals("ERROR hello\n", unfiltered.toString());
	}

	/*
	 * A filter as a plugin would ship it, registered by a configurator and selected
	 * purely by property.
	 */
	static final class SlideConfigurator implements RainbowGumServiceProvider.Configurator {

		@Override
		public boolean configure(LogConfig config, Pass pass) {
			registerSlide(config);
			return true;
		}

	}

	@Test
	void filterRegisteredByAConfigurator() {
		var output = new ListLogOutput();
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties("logging.route.default.filters=slide").build())
			.configurator(new SlideConfigurator())
			.build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output).formatter(FORMATTER)))
			.build()
			.start()) {
			gum.log(LogEventFactory.of("test").eventNoArg(Level.ERROR, "hello", KeyValues.of(), (Throwable) null));
		}
		assertEquals("WARN hello\n", output.toString());
	}

	@Test
	void registeringANameTwiceKeepsTheFirst() {
		String actual = log("""
				logging.level=WARNING
				logging.route.default.filters=slide
				""", c -> {
			registerSlide(c);
			Filter.register(c.serviceRegistry(), "slide", (name, config) -> e -> null);
		});
		assertEquals("""
				WARN hello
				""", actual);
	}

	@Test
	void unknownFilterFails() {
		var e = assertThrows(IllegalArgumentException.class,
				() -> log("logging.route.default.filters=slide,nope", RouteFilterTest::registerSlide));
		assertEquals("""
				Unknown filter 'nope' for route 'default'. Available filters: slide""", e.getMessage());
	}

	@Test
	void unknownFilterFailsWhenNoneAreRegistered() {
		var e = assertThrows(IllegalArgumentException.class, () -> log("logging.route.default.filters=nope", c -> {
		}));
		assertEquals("""
				Unknown filter 'nope' for route 'default'. Available filters: none""", e.getMessage());
	}

}
