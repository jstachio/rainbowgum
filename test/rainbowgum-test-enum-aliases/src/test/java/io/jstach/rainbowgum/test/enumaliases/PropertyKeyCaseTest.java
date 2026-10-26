package io.jstach.rainbowgum.test.enumaliases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/*
 * Every segment of a property name but the last is lowercase, like a package name:
 * logging.messageformat.style, not logging.messageFormat.style. The names are read from
 * the constant pool of each compiled class, so constants concatenated from prefixes,
 * package private constants and the literal parts of a runtime concatenation are all
 * checked without loading any class.
 */
class PropertyKeyCaseTest {

	static final String PACKAGE_DIR = "io/jstach/rainbowgum/";

	static final String ROOT_PREFIX = "logging.";

	@Test
	void everyNonLastSegmentIsLowercase() throws Exception {
		Map<String, Set<String>> violations = new TreeMap<>();
		for (var e : scannedModules().entrySet()) {
			Path root = e.getValue();
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
					try (var in = Files.newInputStream(file)) {
						for (String s : strings(in)) {
							String key = keyOrNull(s);
							if (key != null && !isLowercaseUpToLastSegment(key)) {
								violations.computeIfAbsent(key, k -> new TreeSet<>()).add(e.getKey());
							}
						}
					}
				}
			}
		}
		assertEquals(Map.of(), violations);
	}

	/*
	 * Guards against the scan silently finding nothing, for example if the jars stop
	 * having directory entries.
	 */
	@Test
	void everyModuleIsScanned() throws Exception {
		assertEquals(Set.of( //
				"io.jstach.rainbowgum", //
				"io.jstach.rainbowgum.annotation", //
				"io.jstach.rainbowgum.disruptor", //
				"io.jstach.rainbowgum.file", //
				"io.jstach.rainbowgum.jansi", //
				"io.jstach.rainbowgum.jbosslogging", //
				"io.jstach.rainbowgum.jfr", //
				"io.jstach.rainbowgum.json", //
				"io.jstach.rainbowgum.jul", //
				"io.jstach.rainbowgum.jul.logmanager", //
				"io.jstach.rainbowgum.keyvalues.contributor", //
				"io.jstach.rainbowgum.opentelemetry", //
				"io.jstach.rainbowgum.otlp", //
				"io.jstach.rainbowgum.pattern", //
				"io.jstach.rainbowgum.simple.props", //
				"io.jstach.rainbowgum.slf4j", //
				"io.jstach.rainbowgum.systemlogger"), scannedModules().keySet());
	}

	@Test
	void keysAreTakenFromTheStartOfAString() {
		assertEquals("logging.messageFormat.style", keyOrNull("logging.messageFormat.style=jdk is set"));
		assertEquals("logging.appender.\u0001.encoder.uri", keyOrNull("logging.appender.\u0001.encoder.uri"));
		assertEquals(null, keyOrNull("see logging.messageFormat.style"));
	}

	@Test
	void onlyTheLastSegmentMayHaveUppercase() {
		assertEquals(false, isLowercaseUpToLastSegment("logging.messageFormat.style"));
		assertEquals(false, isLowercaseUpToLastSegment("logging.fakeGlobal."));
		assertEquals(true, isLowercaseUpToLastSegment("logging.global.threadLocal"));
		assertEquals(true, isLowercaseUpToLastSegment("logging.appender.{name}.encoder.postProcessors"));
	}

	/*
	 * Java module name -> class root, for every root on the classpath with classes under
	 * io/jstach/rainbowgum except this test module's own.
	 */
	static Map<String, Path> scannedModules() throws IOException, URISyntaxException {
		URI self = PropertyKeyCaseTest.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		Map<String, Path> result = new TreeMap<>();
		var urls = PropertyKeyCaseTest.class.getClassLoader().getResources(PACKAGE_DIR);
		for (var url : Collections.list(urls)) {
			Path packageDir = path(url.toURI());
			Path root = packageDir.getRoot() != null && packageDir.getFileSystem() != FileSystems.getDefault()
					? packageDir.getRoot() : packageDir.resolve("../../..").normalize();
			if (root.toUri().equals(self)) {
				continue;
			}
			try (var in = Files.newInputStream(root.resolve("module-info.class"))) {
				result.put(ModuleDescriptor.read(in).name(), root);
			}
		}
		return result;
	}

	private static Path path(URI uri) throws IOException {
		if ("jar".equals(uri.getScheme())) {
			FileSystem fs;
			try {
				fs = FileSystems.getFileSystem(uri);
			}
			catch (FileSystemNotFoundException e) {
				fs = FileSystems.newFileSystem(uri, Map.of());
			}
			return fs.provider().getPath(uri);
		}
		return Path.of(uri);
	}

	static @Nullable String keyOrNull(String s) {
		if (!s.startsWith(ROOT_PREFIX)) {
			return null;
		}
		int end = 0;
		while (end < s.length() && isKeyChar(s.charAt(end))) {
			end++;
		}
		return s.substring(0, end);
	}

	/*
	 * \u0001 is where a runtime concatenation inserts a value.
	 */
	private static boolean isKeyChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == '{' || c == '}'
				|| c == '\u0001';
	}

	static boolean isLowercaseUpToLastSegment(String key) {
		String[] segments = key.split("\\.", -1);
		for (int i = 0; i < segments.length - 1; i++) {
			if (!segments[i].equals(segments[i].toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		return true;
	}

	/*
	 * The CONSTANT_Utf8 entries of a class file's constant pool, which hold every string
	 * literal and every compile time constant the class uses.
	 */
	static List<String> strings(InputStream in) throws IOException {
		var data = new DataInputStream(new BufferedInputStream(in));
		if (data.readInt() != 0xCAFEBABE) {
			throw new IOException("Not a class file");
		}
		data.readUnsignedShort(); // minor
		data.readUnsignedShort(); // major
		int count = data.readUnsignedShort();
		List<String> result = new ArrayList<>();
		for (int i = 1; i < count; i++) {
			int tag = data.readUnsignedByte();
			switch (tag) {
				case 1 -> result.add(data.readUTF());
				case 7, 8, 16, 19, 20 -> data.skipNBytes(2);
				case 15 -> data.skipNBytes(3);
				case 3, 4, 9, 10, 11, 12, 17, 18 -> data.skipNBytes(4);
				case 5, 6 -> {
					data.skipNBytes(8);
					i++; // takes two entries
				}
				default -> throw new IOException("Unknown constant pool tag " + tag);
			}
		}
		return result;
	}

}
