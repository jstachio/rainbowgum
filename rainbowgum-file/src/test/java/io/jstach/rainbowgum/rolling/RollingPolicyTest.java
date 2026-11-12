package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

	@ParameterizedTest
	@ValueSource(strings = { "rw-------", "rw-r-----", "rw-r--r--", "r--r-----" })
	void gzipArchiveKeepsTheActiveFilePermissions(String mode) throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(active, "hello");
		assumeTrue(Files.getFileAttributeView(active, PosixFileAttributeView.class) != null, "POSIX only");
		var permissions = PosixFilePermissions.fromString(mode);
		Files.setPosixFilePermissions(active, permissions);
		var owner = Files.getOwner(active);
		RollingPolicy.roll(active, ParsedPattern.parse(".%i.gz"), 1, 0);
		assertEquals(permissions, Files.getPosixFilePermissions(dir.resolve("app.log.1.gz")));
		assertEquals(owner, Files.getOwner(dir.resolve("app.log.1.gz")));
		try (var in = new GZIPInputStream(Files.newInputStream(dir.resolve("app.log.1.gz")))) {
			assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void gzipArchiveKeepsTheSourceGroupWhenItDiffersFromTheDirectory() throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(active, "private group log");
		assumeTrue(Files.getFileAttributeView(active, PosixFileAttributeView.class) != null, "POSIX only");
		int initialGroup = ((Number) Files.getAttribute(active, "unix:gid")).intValue();
		var status = Path.of("/proc/self/status");
		assumeTrue(Files.isReadable(status), "Requires Linux process group information");
		String groups = Files.readAllLines(status)
			.stream()
			.filter(line -> line.startsWith("Groups:"))
			.findFirst()
			.orElse("");
		int alternateGroup = initialGroup;
		for (String group : groups.replace("Groups:", "").strip().split("\\s+", -1)) {
			if (!group.isEmpty() && Integer.parseInt(group) != initialGroup) {
				alternateGroup = Integer.parseInt(group);
				break;
			}
		}
		assumeTrue(alternateGroup != initialGroup, "Requires a supplementary group");
		Files.setAttribute(active, "unix:gid", alternateGroup);
		var permissions = PosixFilePermissions.fromString("rw-r-----");
		Files.setPosixFilePermissions(active, permissions);

		RollingPolicy.roll(active, ParsedPattern.parse(".%i.gz"), 1, 0);

		Path archive = dir.resolve("app.log.1.gz");
		assertEquals(alternateGroup, ((Number) Files.getAttribute(archive, "unix:gid")).intValue());
		assertEquals(permissions, Files.getPosixFilePermissions(archive));
	}

	@Test
	void gzipFailureKeepsThePendingFileAndRemovesTheTemporaryArchive() throws IOException {
		Path active = dir.resolve("app.log");
		Path pending = RollingPolicy.pendingPath(active);
		Files.writeString(active, "keep this log");
		assumeTrue(Files.getFileAttributeView(active, PosixFileAttributeView.class) != null, "POSIX only");
		Files.setPosixFilePermissions(active, Set.of());
		try {
			assumeTrue(!Files.isReadable(active), "Requires enforced read permissions");
			assertThrows(UncheckedIOException.class,
					() -> RollingPolicy.roll(active, ParsedPattern.parse(".%i.gz"), 1, 0));
		}
		finally {
			Files.setPosixFilePermissions(Files.exists(pending) ? pending : active,
					PosixFilePermissions.fromString("rw-------"));
		}
		assertEquals("keep this log", Files.readString(pending));
		try (var files = Files.list(dir)) {
			assertEquals(List.of(pending), files.toList());
		}
	}

	@Test
	void gzipRollOnlyRenamesUntilCompressed() throws IOException {
		Path active = dir.resolve("app.log");
		Path pending = RollingPolicy.pendingPath(active);
		var pattern = ParsedPattern.parse(".%i.gz");
		Files.writeString(active, "first");
		Files.writeString(dir.resolve("app.log.1.gz"), "older");
		assertTrue(RollingPolicy.rollUncompressed(active, pattern, 3, 0));
		assertEquals("first", Files.readString(pending));
		assertEquals("older", Files.readString(dir.resolve("app.log.2.gz")));
		assertFalse(Files.exists(active));
		assertFalse(Files.exists(dir.resolve("app.log.1.gz")));
		RollingPolicy.compress(active, pattern, 3, 0);
		assertFalse(Files.exists(pending));
		try (var in = new GZIPInputStream(Files.newInputStream(dir.resolve("app.log.1.gz")))) {
			assertEquals("first", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void aLeftoverPendingFileIsCompressedBeforeTheNextRoll() throws IOException {
		Path active = dir.resolve("app.log");
		Path pending = RollingPolicy.pendingPath(active);
		var pattern = ParsedPattern.parse(".%i.gz");
		Files.writeString(pending, "left over");
		Files.writeString(active, "current");
		assertTrue(RollingPolicy.rollUncompressed(active, pattern, 3, 0));
		assertEquals("current", Files.readString(pending));
		try (var in = new GZIPInputStream(Files.newInputStream(dir.resolve("app.log.2.gz")))) {
			assertEquals("left over", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void aPendingPathThatIsNotARegularFileIsNotCompressed() throws IOException {
		Path active = dir.resolve("app.log");
		Path pending = Files.createDirectory(RollingPolicy.pendingPath(active));
		Files.writeString(active, "current");
		assertThrows(UncheckedIOException.class,
				() -> RollingPolicy.rollUncompressed(active, ParsedPattern.parse(".%i.gz"), 3, 0));
		assertEquals("current", Files.readString(active));
		assertTrue(Files.isDirectory(pending));
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
