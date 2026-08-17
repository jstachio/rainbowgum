/**
 * Provides
 * <a href="https://logback.qos.ch/manual/layouts.html#ClassicPatternLayout">Logback style
 * pattern formatters.</a> The URI scheme of pattern encoders is
 * {@value io.jstach.rainbowgum.pattern.format.PatternEncoder#PATTERN_SCHEME}.
 *
 * <p>
 * The supported builtin keywords are in the follow enum types:
 * <ul>
 * <li>{@link io.jstach.rainbowgum.pattern.format.PatternRegistry.KeywordKey}</li>
 * <li>{@link io.jstach.rainbowgum.pattern.format.PatternRegistry.ColorKey}</li>
 * </ul>
 * <strong>Rainbow Gum does not currently support all of the builtin keywords that Logback
 * does!</strong> But most of them are available.
 *
 * <h2>Adding a custom keyword</h2>
 *
 * Extend {@link io.jstach.rainbowgum.pattern.format.spi.PatternKeywordProvider} and
 * register a
 * {@link io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.KeywordFactory} (or
 * {@link io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.CompositeFactory} if
 * the keyword should accept a child pattern like {@code %keyword(child)}) with the
 * {@link io.jstach.rainbowgum.pattern.format.PatternRegistry}. This example adds
 * {@code %hostname}, resolved once rather than on every event:
 *
 * {@snippet class = "snippets.CustomPatternKeywordExample" region =
 * "customPatternKeyword" }
 *
 * Register it like any other {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider}.
 * If your application is modularized:
 *
 * {@snippet :
 *
 * provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with com.mycompany.CustomPatternKeywordExample;
 *
 * }
 *
 * <h2>Configuring {@link io.jstach.rainbowgum.pattern.format.PatternConfig}</h2>
 *
 * {@link io.jstach.rainbowgum.pattern.format.PatternConfig} carries platform specific
 * settings (time zone, line separator, whether ANSI is disabled, the {@code %r} start
 * time) that keywords need. <strong>Prefer
 * {@linkplain io.jstach.rainbowgum.pattern.format.PatternConfig#PATTERN_CONFIG_PREFIX
 * property configuration}</strong> (see the user guide's Pattern Module section) since it
 * is resolved per encoder name and needs no code. To instead set a programmatic default
 * used by every encoder that has no more specific property configuration, register a
 * {@link io.jstach.rainbowgum.pattern.format.PatternConfig} - which is itself a
 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator} - with
 * {@link io.jstach.rainbowgum.LogConfig.Builder#configurator(io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator)}:
 *
 * {@snippet class = "snippets.PatternConfigExample" region = "patternConfigExample" }
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.pattern.format;
