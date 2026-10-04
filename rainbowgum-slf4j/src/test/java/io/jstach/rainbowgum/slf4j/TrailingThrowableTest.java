package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.BiConsumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * A trailing Throwable argument is always the event's throwable, even when a {}
 * placeholder would otherwise consume it, matching Logback. That placeholder is left as a
 * literal {}. Log4j2 instead renders the exception's toString and drops the stack trace,
 * which users migrating from Logback reported losing (apache/logging-log4j2#2363).
 */
class TrailingThrowableTest {

	@SuppressWarnings("ImmutableEnumChecker") // stateless lambdas
	enum Call {

		LOG4J2_ISSUE_2363((log, ex) -> log.error("fail to process {}, the error is:{}", null, ex),
				"fail to process null, the error is:{}"),
		ONE_ARG_CONSUMED((log, ex) -> log.error("error: {}", ex), "error: {}"),
		ONE_ARG_NOT_CONSUMED((log, ex) -> log.error("error", ex), "error"),
		TWO_ARGS_NOT_CONSUMED((log, ex) -> log.error("a={}", "a", ex), "a=a"),
		THREE_ARGS_CONSUMED((log, ex) -> log.error("{} {} {}", "a", "b", ex), "a b {}"),
		THREE_ARGS_NOT_CONSUMED((log, ex) -> log.error("{} {}", "a", "b", ex), "a b"),
		FLUENT_ADD_ARGUMENT((log, ex) -> log.atError().setMessage("error: {}").addArgument(ex).log(), "error: {}"),
		FLUENT_SET_CAUSE((log, ex) -> log.atError().setMessage("a={}").addArgument("a").setCause(ex).log(), "a=a");

		final BiConsumer<Logger, Throwable> call;

		final String expectedMessage;

		Call(BiConsumer<Logger, Throwable> call, String expectedMessage) {
			this.call = call;
			this.expectedMessage = expectedMessage;
		}

	}

	@ParameterizedTest
	@EnumSource(Call.class)
	void trailingThrowableIsAttachedLikeLogback(Call call) {
		var ex = new NullPointerException("npe");
		var list = new ListLogOutput();
		var gum = RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(list)))
			.build();
		try (var g = gum.start()) {
			var log = new RainbowGumLoggerFactory(g, new RainbowGumMDCAdapter()).getLogger("test");
			call.call.accept(log, ex);
		}
		assertEquals(1, list.events().size());
		var event = list.events().get(0).getKey();
		var message = new StringBuilder();
		event.formattedMessage(message);
		assertEquals(call.expectedMessage + " | " + ex, message + " | " + event.throwableOrNull());
	}

}
