package io.jstach.rainbowgum.rolling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
		if (rollUncompressed(activeFile, pattern, maxHistory, totalSizeCap)) {
			compress(activeFile, pattern, maxHistory, totalSizeCap);
		}
	}

	/**
	 * Where the active file waits to be compressed into archive slot 1.
	 * @param activeFile the file being actively written to.
	 * @return path next to the active file.
	 */
	static Path pendingPath(Path activeFile) {
		return Path.of(activeFile + ".pending");
	}

	/**
	 * Like {@link #roll}, except that for a gzip pattern the active file is only renamed
	 * to {@link #pendingPath(Path)}, so the slow part can run without blocking writers. A
	 * pending file left by an earlier failed or interrupted compression is compressed
	 * first, so it is never overwritten. An active symbolic link uses synchronous gzip,
	 * as before: compress its target and remove only the link. Moving that link into the
	 * pending path would leave a nonregular entry that recovery must not follow.
	 * @param activeFile the file being actively written to.
	 * @param pattern parsed {@code fileNamePattern}.
	 * @param maxHistory maximum number of archives to retain.
	 * @param totalSizeCap maximum combined size, in bytes, of all archives; {@code <= 0}
	 * means unlimited.
	 * @return true if {@link #compress} must be called to finish the roll.
	 */
	static boolean rollUncompressed(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap) {
		try {
			compress(activeFile, pattern, maxHistory, totalSizeCap);
			if (maxHistory > 0) {
				Files.deleteIfExists(pattern.archivePath(activeFile, maxHistory));
			}
			for (int n = maxHistory - 1; n >= 1; n--) {
				Path from = pattern.archivePath(activeFile, n);
				if (Files.exists(from)) {
					Files.move(from, pattern.archivePath(activeFile, n + 1), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			if (maxHistory >= 1 && Files.exists(activeFile)) {
				if (pattern.gzip()) {
					if (Files.isSymbolicLink(activeFile)) {
						gzip(activeFile, pattern.archivePath(activeFile, 1));
						Files.delete(activeFile);
						enforceTotalSizeCap(activeFile, pattern, maxHistory, totalSizeCap);
						return false;
					}
					Files.move(activeFile, pendingPath(activeFile));
					return true;
				}
				Files.move(activeFile, pattern.archivePath(activeFile, 1), StandardCopyOption.REPLACE_EXISTING);
			}
			else {
				Files.deleteIfExists(activeFile);
			}
			enforceTotalSizeCap(activeFile, pattern, maxHistory, totalSizeCap);
			return false;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Compresses the {@link #pendingPath(Path) pending file}, if there is one, into
	 * archive slot 1 and then enforces {@code totalSizeCap}. If compression fails the
	 * pending file is kept for the next attempt.
	 * @param activeFile the file being actively written to.
	 * @param pattern parsed {@code fileNamePattern}.
	 * @param maxHistory maximum number of archives to retain.
	 * @param totalSizeCap maximum combined size, in bytes, of all archives; {@code <= 0}
	 * means unlimited.
	 */
	static void compress(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap) {
		Path pending = pendingPath(activeFile);
		if (!pattern.gzip() || Files.notExists(pending, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try {
			if (!Files.isRegularFile(pending, LinkOption.NOFOLLOW_LINKS)) {
				throw new FileSystemException(pending.toString(), null, "not a regular file");
			}
			if (maxHistory >= 1) {
				gzip(pending, pattern.archivePath(activeFile, 1));
			}
			Files.delete(pending);
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
	 * @return matching entries that are not regular files with a canonical positive
	 * integer index. They are left alone and excluded from the size cap.
	 */
	static List<Path> cleanHistory(Path activeFile, ParsedPattern pattern, int maxHistory, long totalSizeCap) {
		try {
			List<Path> skipped = new ArrayList<>();
			for (int index : existingArchiveIndexes(activeFile, pattern, skipped)) {
				if (index > maxHistory) {
					Files.deleteIfExists(pattern.archivePath(activeFile, index));
				}
			}
			enforceTotalSizeCap(activeFile, pattern, maxHistory, totalSizeCap);
			return skipped;
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
			BasicFileAttributes attributes;
			try {
				attributes = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			}
			catch (NoSuchFileException e) {
				continue;
			}
			if (!attributes.isRegularFile()) {
				continue;
			}
			if (evict) {
				Files.delete(p);
				continue;
			}
			total += attributes.size();
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
	private static List<Integer> existingArchiveIndexes(Path activeFile, ParsedPattern pattern, List<Path> skipped)
			throws IOException {
		Path parent = activeFile.toAbsolutePath().getParent();
		if (parent == null || !Files.isDirectory(parent)) {
			return List.of();
		}
		var regex = pattern.indexRegex(activeFile.toAbsolutePath());
		List<Integer> indexes = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
			for (Path candidate : stream) {
				var matcher = regex.matcher(Objects.requireNonNull(candidate.getFileName()).toString());
				if (matcher.matches()) {
					try {
						String text = Objects.requireNonNull(matcher.group(1));
						int index = Integer.parseInt(text);
						if (index > 0 && text.equals(Integer.toString(index))
								&& Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
							indexes.add(index);
						}
						else {
							skipped.add(candidate);
						}
					}
					catch (NumberFormatException e) {
						// Too large to be an index we wrote, so not one of our archives.
						skipped.add(candidate);
					}
				}
			}
		}
		return indexes;
	}

	private static void gzip(Path source, Path target) throws IOException {
		Path directory = Objects.requireNonNull(target.getParent());
		Path fileName = Objects.requireNonNull(target.getFileName());
		Path temporary = Files.createTempFile(directory, fileName.toString(), ".tmp");
		try {
			try (var in = Files.newInputStream(source);
					var out = new GZIPOutputStream(Files.newOutputStream(temporary))) {
				in.transferTo(out);
			}
			copyPosixAccess(source, temporary);
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(temporary);
			}
			catch (IOException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		}
	}

	private static void copyPosixAccess(Path source, Path target) throws IOException {
		var sourceView = Files.getFileAttributeView(source, PosixFileAttributeView.class);
		if (sourceView == null) {
			return;
		}
		var sourceAttributes = sourceView.readAttributes();
		var targetView = Objects.requireNonNull(
				Files.getFileAttributeView(target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS));
		var targetAttributes = targetView.readAttributes();
		if (!sourceAttributes.owner().equals(targetAttributes.owner())) {
			// Keep the contents inaccessible while changing the owner and group.
			targetView.setPermissions(Set.of());
			targetView.setOwner(sourceAttributes.owner());
		}
		if (!sourceAttributes.group().equals(targetAttributes.group())) {
			targetView.setGroup(sourceAttributes.group());
		}
		// Apply access bits only after they refer to the correct owner and group.
		// Failure propagates so the temporary file is removed and the source survives.
		targetView.setPermissions(sourceAttributes.permissions());
	}

}
