package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.rolling.RollingPolicy.ParsedPattern;

class RollingPolicyTest {

	@TempDir
	Path dir;

	@Test
	void parseRequiresPercentI() {
		assertThrows(IllegalArgumentException.class, () -> ParsedPattern.parse("archive"));
	}

	@Test
	void parseRejectsPercentD() {
		var e = assertThrows(IllegalArgumentException.class, () -> ParsedPattern.parse("%d{yyyy-MM-dd}.%i"));
		assertTrue(Objects.requireNonNull(e.getMessage()).contains("%d"));
	}

	@Test
	void parseRejectsMoreThanOnePercentI() {
		assertThrows(IllegalArgumentException.class, () -> ParsedPattern.parse("%i.%i"));
	}

	@Test
	void parseSplitsPrefixAndSuffix() {
		var p = ParsedPattern.parse(".%i.gz");
		assertEquals(".", p.prefix());
		assertEquals(".gz", p.suffix());
		assertTrue(p.gzip());
	}

	@Test
	void rollMovesActiveFileToArchiveOne() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "hello");
		var pattern = ParsedPattern.parse(".%i");

		RollingPolicy.roll(active, pattern, 7, 0);

		assertFalse(Files.exists(active));
		assertEquals("hello", Files.readString(dir.resolve("app.log.1")));
	}

	@Test
	void rollShiftsExistingArchivesUp() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "newest");
		Files.writeString(dir.resolve("app.log.1"), "was-1");
		Files.writeString(dir.resolve("app.log.2"), "was-2");
		var pattern = ParsedPattern.parse(".%i");

		RollingPolicy.roll(active, pattern, 7, 0);

		assertEquals("newest", Files.readString(dir.resolve("app.log.1")));
		assertEquals("was-1", Files.readString(dir.resolve("app.log.2")));
		assertEquals("was-2", Files.readString(dir.resolve("app.log.3")));
	}

	@Test
	void rollDropsArchivesBeyondMaxHistory() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "newest");
		Files.writeString(dir.resolve("app.log.1"), "was-1");
		Files.writeString(dir.resolve("app.log.2"), "was-2");
		var pattern = ParsedPattern.parse(".%i");

		RollingPolicy.roll(active, pattern, 2, 0);

		assertEquals("newest", Files.readString(dir.resolve("app.log.1")));
		assertEquals("was-1", Files.readString(dir.resolve("app.log.2")));
		assertFalse(Files.exists(dir.resolve("app.log.3")), "was-2 must have been evicted, not shifted to .3");
	}

	@Test
	void maxHistoryZeroKeepsNoArchive() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "gone");
		var pattern = ParsedPattern.parse(".%i");

		RollingPolicy.roll(active, pattern, 0, 0);

		assertFalse(Files.exists(active));
		assertFalse(Files.exists(dir.resolve("app.log.1")));
	}

	@Test
	void gzipPatternCompressesArchive() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "compress-me");
		var pattern = ParsedPattern.parse(".%i.gz");

		RollingPolicy.roll(active, pattern, 7, 0);

		var archive = dir.resolve("app.log.1.gz");
		assertTrue(Files.exists(archive));
		try (var in = new GZIPInputStream(Files.newInputStream(archive))) {
			assertEquals("compress-me", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void totalSizeCapEvictsOldestArchivesFirst() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(active, "x".repeat(10));
		Files.writeString(dir.resolve("app.log.1"), "x".repeat(10));
		Files.writeString(dir.resolve("app.log.2"), "x".repeat(10));
		var pattern = ParsedPattern.parse(".%i");

		// roll shifts current archives to .2/.3, moves active -> .1, so the resulting
		// three archives are 30 bytes total; a cap of 15 must keep only the newest.
		RollingPolicy.roll(active, pattern, 7, 15);

		assertTrue(Files.exists(dir.resolve("app.log.1")), "newest archive must survive the cap");
		assertFalse(Files.exists(dir.resolve("app.log.2")), "older archives must be evicted once the cap is exceeded");
		assertFalse(Files.exists(dir.resolve("app.log.3")));
	}

	@Test
	void cleanHistoryOnNonExistentParentDirectoryDoesNothing() throws IOException {
		var active = dir.resolve("does-not-exist-yet/app.log");
		var pattern = ParsedPattern.parse(".%i");

		assertDoesNotThrow(() -> RollingPolicy.cleanHistory(active, pattern, 1, 0));
	}

	@Test
	void cleanHistorySkipsIndexesTooLargeToBeOurs() throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(active, "active");
		Path old = dir.resolve("app.log.3");
		Files.writeString(old, "three");
		Path foreign = dir.resolve("app.log.99999999999");
		Files.writeString(foreign, "not ours");
		var skipped = RollingPolicy.cleanHistory(active, ParsedPattern.parse(".%i"), 1, 0);
		assertEquals(List.of(foreign), skipped);
		assertTrue(Files.exists(foreign));
		assertFalse(Files.exists(old));
	}

	@Test
	void cleanHistoryReportsNonCanonicalIndexesWithoutTouchingThem() throws IOException {
		var active = dir.resolve("app.log");
		var foreign = List.of(dir.resolve("app.log.0"), dir.resolve("app.log.0003"), dir.resolve("app.log.2147483648"));
		for (var path : foreign) {
			Files.writeString(path, "foreign");
		}
		Files.writeString(dir.resolve("app.log.3"), "old");
		var skipped = RollingPolicy.cleanHistory(active, ParsedPattern.parse(".%i"), 1, 0);
		assertEquals(Set.copyOf(foreign), Set.copyOf(skipped));
		for (var path : foreign) {
			assertEquals("foreign", Files.readString(path));
		}
		assertFalse(Files.exists(dir.resolve("app.log.3")));
	}

	@Test
	void cleanHistorySkipsDirectoriesWhenApplyingTheSizeCap() throws IOException {
		var active = dir.resolve("app.log");
		var foreign = Files.createDirectory(dir.resolve("app.log.1"));
		var archive = dir.resolve("app.log.2");
		Files.writeString(archive, "ok");
		var skipped = RollingPolicy.cleanHistory(active, ParsedPattern.parse(".%i"), 2, 2);
		assertEquals(List.of(foreign), skipped);
		assertTrue(Files.isDirectory(foreign));
		assertEquals("ok", Files.readString(archive));
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void cleanHistoryLeavesSymbolicLinksAndTheirTargetsAlone() throws IOException {
		var active = dir.resolve("app.log");
		var target = Files.writeString(dir.resolve("foreign.log"), "foreign");
		var first = Files.createSymbolicLink(dir.resolve("app.log.1"), target);
		var third = Files.createSymbolicLink(dir.resolve("app.log.3"), target);
		var archive = Files.writeString(dir.resolve("app.log.2"), "ok");
		var skipped = RollingPolicy.cleanHistory(active, ParsedPattern.parse(".%i"), 2, 2);
		assertEquals(Set.of(first, third), Set.copyOf(skipped));
		assertTrue(Files.isSymbolicLink(first));
		assertTrue(Files.isSymbolicLink(third));
		assertEquals("foreign", Files.readString(target));
		assertEquals("ok", Files.readString(archive));
	}

	@Test
	void cleanHistoryDoesNotDeleteAnEmptyForeignDirectoryBeyondRetention() throws IOException {
		var active = dir.resolve("app.log");
		var foreign = Files.createDirectory(dir.resolve("app.log.3"));
		assertEquals(List.of(foreign), RollingPolicy.cleanHistory(active, ParsedPattern.parse(".%i"), 1, 0));
		assertTrue(Files.isDirectory(foreign));
	}

	@Test
	void cleanHistoryDropsArchivesBeyondLoweredMaxHistory() throws IOException {
		var active = dir.resolve("app.log");
		Files.writeString(dir.resolve("app.log.1"), "keep");
		Files.writeString(dir.resolve("app.log.2"), "drop");
		Files.writeString(dir.resolve("app.log.3"), "drop");
		var pattern = ParsedPattern.parse(".%i");

		RollingPolicy.cleanHistory(active, pattern, 1, 0);

		assertTrue(Files.exists(dir.resolve("app.log.1")));
		assertFalse(Files.exists(dir.resolve("app.log.2")));
		assertFalse(Files.exists(dir.resolve("app.log.3")));
	}

}
