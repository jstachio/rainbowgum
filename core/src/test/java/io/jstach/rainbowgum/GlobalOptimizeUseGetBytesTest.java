package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogOutput.WriteMethod;

/*
 * Mutates the NATIVE_IMAGE_CODE_PROPERTY system property. DefaultAppenderSelectionTest
 * covers the LogAppender.AppenderType side of LogProperties#GLOBAL_OPTIMIZE_PROPERTY;
 * this class covers the LogEncoder side.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalOptimizeUseGetBytesTest {

	private static final LogFormatter FORMATTER = LogFormatter.builder().message().build();

	private static final LogConfig CONFIG = LogConfig.builder().build();

	private static final LogConfig OPTIMIZE = LogConfig.builder().optimize(true).build();

	final @Nullable String originalNativeImageCodeProperty = System
		.getProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY);

	@AfterEach
	void after() {
		// see DefaultAppenderSelectionTest's own note on why "" instead of
		// System.clearProperty(...).
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY,
				originalNativeImageCodeProperty == null ? "" : originalNativeImageCodeProperty);
	}

	@Test
	void unspecifiedUseGetBytesStaysDirectByteBufferBufferWhenGlobalOptimizeDisabled() {
		var buffer = buffer(null);
		assertInstanceOf(DirectByteBufferBuffer.class, buffer);
	}

	@Test
	void unspecifiedUseGetBytesStaysDirectByteBufferBufferOutsideNativeImageEvenWhenGlobalOptimizeEnabled() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "");
		var buffer = buffer(null, OPTIMIZE);
		assertInstanceOf(DirectByteBufferBuffer.class, buffer);
	}

	@Test
	void unspecifiedUseGetBytesResolvesToStringBuilderBufferBytesInsideNativeImageWhenGlobalOptimizeEnabled() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var buffer = buffer(null, OPTIMIZE);
		assertInstanceOf(StringBuilderBufferBytes.class, buffer);
	}

	@Test
	void explicitUseGetBytesFalseWinsOverGlobalOptimizeInsideNativeImage() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var buffer = buffer(false, OPTIMIZE);
		assertInstanceOf(DirectByteBufferBuffer.class, buffer);
	}

	@Test
	void explicitUseGetBytesTrueWinsOverGlobalOptimizeDisabled() {
		var buffer = buffer(true);
		assertInstanceOf(StringBuilderBufferBytes.class, buffer);
	}

	private static LogEncoder.Buffer buffer(@Nullable Boolean useGetBytes) {
		return buffer(useGetBytes, CONFIG);
	}

	private static LogEncoder.Buffer buffer(@Nullable Boolean useGetBytes, LogConfig config) {
		var builder = LogEncoder.builder(FORMATTER);
		if (useGetBytes != null) {
			builder.useGetBytes(useGetBytes);
		}
		var encoder = builder.build().provide("test", config);
		return encoder.buffer(WriteMethod.BYTES);
	}

}
