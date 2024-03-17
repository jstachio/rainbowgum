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
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.pattern.format;