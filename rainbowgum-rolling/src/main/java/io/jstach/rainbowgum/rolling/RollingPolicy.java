package io.jstach.rainbowgum.rolling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * The rotation algorithm itself, independent of {@link io.jstach.rainbowgum.LogOutput} so
 * it can be unit tested directly against a temp directory without going through a full
 * appender/config pipeline. Deliberately size (not calendar/date) triggered - see
 * {@link ParsedPattern#parse(String)} - so numbered suffixes (<code>app.log.1</code>,
 * <code>app.log.2</code>, ...) are the only naming scheme needed, matching classic
 * <code>logrotate</code> numbering rather than Logback's combined <code>%d{...}.%i</code>
 * scheme.
 */
final class RollingPolicy {

	private RollingPolicy() {
	}

	/**
	 * A parsed {@code fileNamePattern} - the literal text before and after the
	 * <code>%i</code> token.
	 *
	 * @param prefix literal text before <code>%i</code>.
	 * @param suffix literal text after <code>%i</code>, e.g. <code>.gz</code> or empty.
	 * @param gzip whether {@code suffix} ends in <code>.gz</code>, meaning archives
	 * should be gzip compressed on rotation rather than just moved.
	 */
	record ParsedPattern(String prefix, String suffix, boolean gzip) {

		/**
		 * Parses a {@code fileNamePattern}. The pattern is always relative to (appended
		 * directly after) the active file's own path - unlike Logback's
		 * {@code fileNamePattern}, it never contains the base file name itself.
		 * @param pattern pattern, must contain exactly one <code>%i</code> token and no
		 * <code>%d</code> token.
		 * @return parsed pattern.
		 * @throws IllegalArgumentException if the pattern contains a <code>%d</code>
		 * (date based) token - not supported, only size based (<code>%i</code>) rotation
		 * is - or does not contain <code>%i</code> at all.
		 */
		static ParsedPattern parse(String pattern) {
			if (pattern.contains("%d")) {
				throw new IllegalArgumentException(
						"fileNamePattern does not support %d (date based) tokens, only %i (rotation index): "
								+ pattern);
			}
			int i = pattern.indexOf("%i");
			if (i < 0) {
				throw new IllegalArgumentException("fileNamePattern must contain %i (rotation index): " + pattern);
			}
			if (pattern.indexOf("%i", i + 2) >= 0) {
				throw new IllegalArgumentException("fileNamePattern must contain only one %i token: " + pattern);
			}
			String prefix = pattern.substring(0, i);
			String suffix = pattern.substring(i + 2);
			boolean gzip = suffix.endsWith(".gz");
			return new ParsedPattern(prefix, suffix, gzip);
		}

		Path archivePath(Path activeFile, int index) {
			return Path.of(activeFile + prefix + index + suffix);
		}

		private Pattern indexRegex(Path activeFile) {
			return Pattern.compile(Pattern.quote(activeFile.getFileName() + prefix) + "(\\d+)" + Pattern.quote(suffix));
		}

	}

	/**
	 * Rolls the active file: shifts existing numbered archives up by one, drops anything
	 * beyond {@code maxHistory}, moves (optionally gzip compressing) the active file into
	 * archive slot 1, then enforces {@code totalSizeCap}. Does not recreate the active
	 * file - the caller is expected to reopen it.
	 * @param activeFile the file being actively written to.
	 * @param pattern parsed {@code fileNamePattern}.
	 * @param maxHistory maximum number of archives to retain; {@code 0} keeps none (the
	 * active file's contents are simply discarded on roll).
	 * @param totalSizeCap maximum combined size, in bytes, of all archives; {@code <= 0}
	 * means unlimited.
	 */
	static void roll(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap) {
		try {
			Files.deleteIfExists(pattern.archivePath(activeFile, maxHistory));
			for (int n = maxHistory - 1; n >= 1; n--) {
				Path from = pattern.archivePath(activeFile, n);
				if (Files.exists(from)) {
					Files.move(from, pattern.archivePath(activeFile, n + 1), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			if (maxHistory >= 1 && Files.exists(activeFile)) {
				Path target = pattern.archivePath(activeFile, 1);
				if (pattern.gzip()) {
					gzip(activeFile, target);
					Files.delete(activeFile);
				}
				else {
					Files.move(activeFile, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			else {
				Files.deleteIfExists(activeFile);
			}
			enforceTotalSizeCap(activeFile, pattern, maxHistory, totalSizeCap);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prunes archives without rolling the active file - for {@code cleanHistoryOnStart}:
	 * drops any leftover archive whose index exceeds the <em>current</em>
	 * {@code maxHistory} (e.g. after lowering the setting) and then enforces
	 * {@code totalSizeCap}, same as after a normal {@link #roll}.
	 * @param activeFile the file that will be actively written to.
	 * @param pattern parsed {@code fileNamePattern}.
	 * @param maxHistory maximum number of archives to retain.
	 * @param totalSizeCap maximum combined size, in bytes, of all archives; {@code <= 0}
	 * means unlimited.
	 */
	static void cleanHistory(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap) {
		try {
			for (int index : existingArchiveIndexes(activeFile, pattern)) {
				if (index > maxHistory) {
					Files.deleteIfExists(pattern.archivePath(activeFile, index));
				}
			}
			enforceTotalSizeCap(activeFile, pattern, maxHistory, totalSizeCap);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void enforceTotalSizeCap(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap)
			throws IOException {
		if (totalSizeCap <= 0) {
			return;
		}
		long total = 0;
		boolean evict = false;
		for (int n = 1; n <= maxHistory; n++) {
			Path p = pattern.archivePath(activeFile, n);
			if (!Files.exists(p)) {
				continue;
			}
			if (evict) {
				Files.delete(p);
				continue;
			}
			total += Files.size(p);
			if (total > totalSizeCap) {
				Files.delete(p);
				evict = true;
			}
		}
	}

	/*
	 * Bounded only by whatever archives actually exist on disk - lists the parent
	 * directory and matches file names against the pattern's prefix/suffix rather than
	 * guessing an upper bound to scan, so a maxHistory lowered across restarts (or a
	 * totalSizeCap that evicted files out of order) is handled correctly either way.
	 */
	private static List<Integer> existingArchiveIndexes(Path activeFile, ParsedPattern pattern) throws IOException {
		Path parent = activeFile.toAbsolutePath().getParent();
		if (parent == null || !Files.isDirectory(parent)) {
			return List.of();
		}
		var regex = pattern.indexRegex(activeFile.toAbsolutePath());
		List<Integer> indexes = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
			for (Path candidate : stream) {
				var matcher = regex.matcher(candidate.getFileName().toString());
				if (matcher.matches()) {
					indexes.add(Integer.parseInt(matcher.group(1)));
				}
			}
		}
		return indexes;
	}

	private static void gzip(Path source, Path target) throws IOException {
		try (var in = Files.newInputStream(source); var out = new GZIPOutputStream(Files.newOutputStream(target))) {
			in.transferTo(out);
		}
	}

}
