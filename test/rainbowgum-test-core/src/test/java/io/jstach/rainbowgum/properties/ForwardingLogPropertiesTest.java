package io.jstach.rainbowgum.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;

class ForwardingLogPropertiesTest {

	/*
	 * Only overrides valueOrNull, the way most decorators would.
	 */
	static final class UpperCasing extends ForwardingLogProperties {

		private final LogProperties delegate;

		final List<String> keys = new ArrayList<>();

		UpperCasing(LogProperties delegate) {
			this.delegate = delegate;
		}

		@Override
		protected LogProperties delegate() {
			return delegate;
		}

		@Override
		public @Nullable String valueOrNull(String key) {
			keys.add(key);
			var value = super.valueOrNull(key);
			return value == null ? null : value.toUpperCase(Locale.ROOT);
		}

	}

	@Test
	void lookupsThroughForKeyReachTheDecorator() {
		var delegate = LogProperties.builder().fromProperties("logging.example.mode=fast").build();
		var properties = new UpperCasing(delegate);
		assertEquals("FAST",
				properties.forKey("logging.example.mode").ofString().validateNow(ForwardingLogPropertiesTest.class));
		assertEquals("FAST",
				properties.forKey("logging.example.missing")
					.or("logging.example.mode")
					.ofString()
					.validateNow(ForwardingLogPropertiesTest.class));
		assertEquals(List.of("logging.example.mode", "logging.example.missing", "logging.example.mode"),
				properties.keys);
	}

	@Test
	void descriptionsAndOrderComeFromTheDelegate() {
		var delegate = LogProperties.builder().fromProperties("logging.example.mode=fast").order(42).build();
		var properties = new UpperCasing(delegate);
		assertEquals(delegate.description("logging.example.mode"), properties.description("logging.example.mode"));
		assertEquals(42, properties.order());
	}

}
