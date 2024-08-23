package io.jstach.rainbowgum.spring.boot;

import org.springframework.boot.logging.CorrelationIdFormatter;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.pattern.PatternKeyword;
import io.jstach.rainbowgum.pattern.format.PatternConfig;
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
	WHITESPACE_EXTENDED_THROWABLE() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node) {
			/*
			 * TODO Actually do the correct outputting based on logback or log4j.
			 */
			return LogFormatter.ThrowableFormatter.of();
		}

		@Override
		public PatternKey key() {
			return PatternKey.of("wEx", "wex");
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

}
