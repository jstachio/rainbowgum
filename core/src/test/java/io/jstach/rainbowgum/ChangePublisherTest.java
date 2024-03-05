package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;

class ChangePublisherTest {

	LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
			logging.change=true
			""").build()).build();

	ChangePublisher changePublisher = new AbstractChangePublisher() {

		@Override
		protected LogConfig reload() {
			return config;
		}

		@Override
		protected LogConfig config() {
			return config;
		}

	};

	@Test
	void test() {
		Set<ChangeType> changes = changePublisher.allowedChanges("anything");
		assertEquals(EnumSet.of(ChangeType.LEVEL, ChangeType.CALLER_INFO), changes);
	}

}
