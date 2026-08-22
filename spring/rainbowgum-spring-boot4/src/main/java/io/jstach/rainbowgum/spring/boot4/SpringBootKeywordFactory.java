package io.jstach.rainbowgum.spring.boot4;

import org.springframework.boot.logging.CorrelationIdFormatter;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.pattern.PatternKeyword;
import io.jstach.rainbowgum.pattern.format.PatternConfig;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.KeywordFactory;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.PatternKey;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.PatternKeyProvider;

enum SpringBootKeywordFactory implements KeywordFactory, PatternKeyProvider {

	CORRELATION_ID() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node) {
			var correlationIdFormatter = CorrelationIdFormatter.of(node.optionList());
			return new CorrelationIdEventFormatter(correlationIdFormatter);
		}

		@Override
		public PatternKey key() {
			return PatternKey.of("correlationId");
		}
	},
	/**
	 * Spring Boot's <code>%wex</code>, backed by
	 * <code>WhitespaceThrowableProxyConverter</code>: a normal throwable formatter
	 * (honoring depth/exclude options) with a leading newline so the stack trace starts
	 * on its own line.
	 */
	WHITESPACE_THROWABLE() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node) {
			var throwableFormatter = PatternFormatterFactory.throwableFormatter(node, false);
			return new WhitespaceThrowableFormatter(throwableFormatter);
		}

		@Override
		public PatternKey key() {
			return PatternKey.of("wex");
		}

		@Override
		public boolean isExceptionFormatter() {
			return true;
		}

	},
	/**
	 * Spring Boot's <code>%wEx</code>, backed by
	 * <code>ExtendedWhitespaceThrowableProxyConverter</code>: like
	 * {@link #WHITESPACE_THROWABLE} but additionally appends packaging data (jar/module
	 * and version) after each frame, matching real Spring Boot's uppercase/lowercase
	 * distinction between <code>%wEx</code> and <code>%wex</code>.
	 */
	EXTENDED_WHITESPACE_THROWABLE() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node) {
			var throwableFormatter = PatternFormatterFactory.throwableFormatter(node, true);
			return new WhitespaceThrowableFormatter(throwableFormatter);
		}

		@Override
		public PatternKey key() {
			return PatternKey.of("wEx");
		}

		@Override
		public boolean isExceptionFormatter() {
			return true;
		}

	};

	record CorrelationIdEventFormatter(CorrelationIdFormatter correlationIdFormatter) implements EventFormatter {

		@Override
		public void format(StringBuilder output, LogEvent event) {
			/*
			 * TODO This is probably not efficient at all but hey its Spring Boot.
			 */
			correlationIdFormatter.formatTo(event.keyValues()::getValueOrNull, output);
		}

	}

	/**
	 * Mirrors Spring Boot's <code>WhitespaceThrowableProxyConverter</code>: prefixes the
	 * delegate's output with a newline so the stack trace is separated from the rest of
	 * the log line. {@link ThrowableFormatter#formatThrowable(StringBuilder, Throwable)}
	 * is only invoked when there is an actual throwable to print, so the newline is never
	 * emitted on its own.
	 */
	record WhitespaceThrowableFormatter(ThrowableFormatter delegate) implements ThrowableFormatter {

		@Override
		public void formatThrowable(StringBuilder output, Throwable throwable) {
			output.append(System.lineSeparator());
			delegate.formatThrowable(output, throwable);
		}

	}

}
