package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.EnumCombinations;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.TestEventBuilder;

class FileOutputTest {

	ListLogOutput list = new ListLogOutput();

	@ParameterizedTest
	@MethodSource("provideArgs")
	void test(FileArg fileArg, RunCount runCount, Events test, BufferArg buffer, PrudentArg prudentArg,
			AppendArg appendArg) throws IOException {
		String fileName = FILE_PATH;

		try {
			for (int i = 0; i < runCount.count; i++) {
				run(fileArg, test, buffer, prudentArg, appendArg);
			}
			String actual = Files.readString(Path.of(fileName));
			int duplicateCount = switch (appendArg) {
				case FALSE -> 1;
				case NOT_SET -> runCount.count;
				case TRUE -> runCount.count;
			};
			String expected = duplicate(test.expected, duplicateCount);
			assertEquals(expected, actual);
		}
		finally {
			Files.deleteIfExists(Path.of(fileName));
		}
	}

	@ParameterizedTest
	@MethodSource("provideArgsNoRunCount")
	void testReopen(FileArg fileArg, Events test, BufferArg buffer, PrudentArg prudentArg, AppendArg appendArg)
			throws IOException {
		String fileName = FILE_PATH;

		int count = 2;
		Files.deleteIfExists(Path.of(fileName));
		for (int i = 0; i < count; i++) {
			String newFile = fileName + "." + i;
			Files.deleteIfExists(Path.of(newFile));
		}
		try {

			var file = file(fileArg, test, buffer, prudentArg, appendArg);
			var gum = makeGum(test, file, list);
			try (var rg = gum.start()) {
				assertTrue(Files.exists(Path.of(fileName)));
				for (var e : test.events()) {
					rg.log(e);
				}
				/*
				 * Now we signal flush.
				 */
				rg.config().outputRegistry().flush();
				{
					String actual = Files.readString(Path.of(fileName));
					String expected = test.expected;
					assertEquals(expected, actual);
				}
				String newFile = fileName + "." + 0;
				/*
				 * We move the to simulate rotation.
				 */
				Files.move(Path.of(fileName), Path.of(newFile));
				/*
				 * We will delete the original file.
				 */
				Files.deleteIfExists(Path.of(fileName));
				assertFalse(Files.exists(Path.of(fileName)));

				/*
				 * Now we signal reopen.
				 */
				var response = rg.config().outputRegistry().reopen();
				{
					String actual = Files.readString(Path.of(newFile));
					String expected = test.expected;
					assertEquals(expected, actual);
				}
				assertEquals("""
						[Response[name=file, status=OK]]
						""".trim(), response.toString());
				assertTrue(Files.exists(Path.of(fileName)));
				for (var e : test.events()) {
					rg.log(e);
				}
				/*
				 * Now we signal flush otherwise there maybe buffering and we cannot test
				 * till flushed.
				 */
				rg.config().outputRegistry().flush();
				{
					String actual = Files.readString(Path.of(fileName));
					String expected = test.expected;
					assertEquals(expected, actual);
				}
				{
					String actual = Files.readString(Path.of(newFile));
					String expected = test.expected;
					assertEquals(expected, actual);
				}

			}

		}
		finally {
			Files.deleteIfExists(Path.of(fileName));
		}
	}

	private void run(FileArg fileArg, Events test, BufferArg buffer, PrudentArg prudentArg, AppendArg appendArg) {
		var file = file(fileArg, test, buffer, prudentArg, appendArg);
		var gum = makeGum(test, file, list);
		try (var rg = gum.start()) {
			for (var e : test.events()) {
				rg.log(e);
			}
		}
	}

	private LogProvider<FileOutput> file(FileArg fileArg, Events test, BufferArg buffer, PrudentArg prudentArg,
			AppendArg appendArg) {
		Integer bufferSize = BufferArg.NULL == buffer ? null : buffer.bufferSize;
		Boolean prudent = prudentArg.prudent();
		var file = FileOutput.of(b -> {
			fileArg.set(b);
			if (bufferSize != null) {
				b.bufferSize(bufferSize);
			}
			if (prudent != null) {
				b.prudent(prudent);
			}
			if (appendArg != AppendArg.NOT_SET) {
				b.append(appendArg.append());
			}
			b.fromProperties(test.fileProperties());
		});
		return file;
	}

	@Test
	void usingBuilderfileNameAndUriBothNOTSetShouldFail() {
		var config = LogConfig.builder().build();

		assertThrows(RuntimeException.class, () -> {
			FileOutput.of(b -> {
				b.fileName(null);
				b.uri(null);
			}).provide("fail", config);
		});

	}

	private static String duplicate(String s, int count) {
		return s.repeat(count);
	}

	static RainbowGum makeGum(Events test, LogProvider<FileOutput> file, ListLogOutput list) {
		var config = LogConfig.builder() //
			.level(test.level()) //
			.build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("file", a -> {
				a.output(file);
			});
			r.appender("list", a -> {
				a.output(list);
			});
		}).build();
		return gum;
	}

	static final String FILE_PATH = "./target/FileOutputTest/file.log";

	enum FileArg {

		FILE_NAME, URI;

		public void set(FileOutputBuilder b) {
			switch (this) {
				case FILE_NAME -> b.fileName(FILE_PATH);
				case URI -> b.uri(Path.of(FILE_PATH).toUri());
			}
		}

	}

	enum Events {

		ZERO("", 0), //
		ONE("""
				00:00:00.000 [main] INFO test - test 0
					"""), //
		TWO("""
				00:00:00.000 [main] INFO test - test 0
				00:00:00.000 [main] INFO test - test 1
									""", 2), //
		THREE("""
				00:00:00.000 [main] INFO test - test 0
				00:00:00.000 [main] INFO test - test 1
				00:00:00.000 [main] INFO test - test 2
									""", 3),;

		final String expected;

		private final int count;

		private Events(String expected) {
			this(expected, 1);
		}

		private Events(String expected, int count) {
			this.expected = expected;
			this.count = count;
		}

		LogProperties fileProperties() {
			return LogProperties.StandardProperties.EMPTY;
		}

		System.Logger.Level level() {
			return System.Logger.Level.INFO;
		}

		List<LogEvent> events() {
			List<LogEvent> events = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				var index = i;
				events.add(TestEventBuilder.of().build(b -> b.message("test " + index)));
			}
			return events;
		}

		@Override
		public String toString() {
			return "EVENTS_" + name();
		}

	}

	enum BufferArg {

		NULL(Integer.MIN_VALUE), NEGATIVE(-1), ZERO(0), ONE(1);

		final Integer bufferSize;

		private BufferArg(Integer bufferSize) {
			this.bufferSize = bufferSize;
		}

		@Override
		public String toString() {
			return "BUFFER_" + name();
		}

	}

	enum PrudentArg {

		NOT_SET, TRUE, FALSE;

		public @Nullable Boolean prudent() {
			return switch (this) {
				case NOT_SET -> null;
				case FALSE -> false;
				case TRUE -> true;
			};
		}

		@Override
		public String toString() {
			return "PRUDENT_" + name();
		}

	}

	enum AppendArg {

		NOT_SET, FALSE, TRUE;

		public boolean append() {
			return switch (this) {
				case NOT_SET -> true;
				case FALSE -> false;
				case TRUE -> true;
			};
		}

		@Override
		public String toString() {
			return "APPEND_" + name();
		}

	}

	enum RunCount {

		ONE(1), TWO(2), THREE(3);

		final int count;

		private RunCount(int count) {
			this.count = count;
		}

		@Override
		public String toString() {
			return "RUN_" + name();
		}

	}

	private static Stream<Arguments> provideArgs() {
		return EnumCombinations.args(FileArg.class, RunCount.class, Events.class, BufferArg.class, PrudentArg.class,
				AppendArg.class);
	}

	private static Stream<Arguments> provideArgsNoRunCount() {
		return EnumCombinations.args(FileArg.class, Events.class, BufferArg.class, PrudentArg.class, AppendArg.class);
	}

}
