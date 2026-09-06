package io.jstach.script;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports the compiled bytecode size of a module's classes grouped by "component" - where
 * a component is a single {@code .java} source file, not a single {@code .class} file.
 *
 * <p>
 * A source file can compile to many {@code .class} files: real nested/inner classes (e.g.
 * {@code LogAppender$Builder.class}) as well as unrelated package-private top-level
 * sibling classes declared in the same file (e.g. {@code LogAppender.java} also contains
 * {@code DirectLogAppender}, {@code CompositeLogAppender}, etc. as their own top-level
 * {@code .class} files with no {@code $} in the name at all). There is no way to tell
 * from a class's own name alone whether it belongs to such a group, so instead every
 * class file's {@code SourceFile} attribute (always the literal originating {@code .java}
 * file name) is read directly from the bytecode and used as the grouping key alongside
 * the class's package.
 *
 * <p>
 * Usage: {@code bin/bytecode-size [classesDir]} (defaults to {@code core/target/classes}
 * relative to the current directory).
 *
 * @author agentgt
 */
public final class BytecodeSize {

	public static void main(String[] args) throws IOException {
		Path classesDir = Path.of(args.length > 0 ? args[0] : "core/target/classes");
		if (!Files.isDirectory(classesDir)) {
			System.err.println("Not a directory (did you build first?): " + classesDir.toAbsolutePath());
			System.exit(1);
			return;
		}
		Map<String, Component> components = new LinkedHashMap<>();
		long grandTotal = 0;
		try (var classFiles = Files.walk(classesDir)) {
			var files = classFiles.filter(p -> p.toString().endsWith(".class")).sorted().toList();
			for (Path classFile : files) {
				long size = Files.size(classFile);
				grandTotal += size;
				String packageName = packageOf(classesDir, classFile);
				String sourceFile = readSourceFile(classFile);
				String key = packageName + "/" + sourceFile;
				Component c = components.computeIfAbsent(key, k -> new Component(packageName, sourceFile));
				c.classFiles++;
				c.bytes += size;
			}
		}

		List<Component> sorted = new ArrayList<>(components.values());
		sorted.sort(Comparator.comparingLong((Component c) -> c.bytes).reversed());

		String header = String.format("%-55s %10s %8s %8s", "COMPONENT", "BYTES", "PERCENT", "CLASSES");
		System.out.println(header);
		System.out.println("-".repeat(header.length()));
		for (Component c : sorted) {
			double percent = grandTotal == 0 ? 0 : (100.0 * c.bytes) / grandTotal;
			System.out.printf("%-55s %,10d %7.2f%% %8d%n", c.label(), c.bytes, percent, c.classFiles);
		}
		System.out.println("-".repeat(header.length()));
		System.out.printf("%-55s %,10d %7.2f%% %8d%n", "TOTAL", grandTotal, 100.0,
				sorted.stream().mapToInt(c -> c.classFiles).sum());
	}

	private static String packageOf(Path classesDir, Path classFile) {
		Path relative = classesDir.relativize(classFile.getParent());
		return relative.toString().replace('/', '.');
	}

	/**
	 * Extracts the {@code SourceFile} attribute value straight from the class file bytes,
	 * without loading the class or shelling out to {@code javap}. Falls back to the class
	 * file's own simple name (minus {@code $Nested} suffix and extension) if the
	 * attribute is somehow absent (e.g. compiled without debug info).
	 */
	private static String readSourceFile(Path classFile) throws IOException {
		try (InputStream in = Files.newInputStream(classFile); var data = new DataInputStream(in)) {
			int magic = data.readInt();
			if (magic != 0xCAFEBABE) {
				throw new IOException("Not a class file: " + classFile);
			}
			data.readUnsignedShort(); // minor version
			data.readUnsignedShort(); // major version

			int constantPoolCount = data.readUnsignedShort();
			String[] utf8 = new String[constantPoolCount];
			for (int i = 1; i < constantPoolCount; i++) {
				int tag = data.readUnsignedByte();
				switch (tag) {
					case 1 -> utf8[i] = data.readUTF(); // Utf8
					case 7, 16, 19, 20 -> data.skipBytes(2); // Class, MethodType, Module,
																// Package
					case 15 -> data.skipBytes(3); // MethodHandle
					case 8 -> data.skipBytes(2); // String
					case 3, 4 -> data.skipBytes(4); // Integer, Float
					case 9, 10, 11, 12, 17, 18 -> data.skipBytes(4); // *ref, NameAndType,
																		// Dynamic,
																		// InvokeDynamic
					case 5, 6 -> { // Long, Double occupy two constant pool slots
						data.skipBytes(8);
						i++;
					}
					default -> throw new IOException("Unknown constant pool tag " + tag + " in " + classFile);
				}
			}

			data.skipBytes(2); // access_flags
			data.skipBytes(2); // this_class
			data.skipBytes(2); // super_class
			int interfacesCount = data.readUnsignedShort();
			data.skipBytes(2 * interfacesCount);

			skipMembers(data); // fields
			skipMembers(data); // methods

			int attributesCount = data.readUnsignedShort();
			for (int i = 0; i < attributesCount; i++) {
				int nameIndex = data.readUnsignedShort();
				long length = data.readInt() & 0xFFFFFFFFL;
				if ("SourceFile".equals(utf8[nameIndex])) {
					int sourceFileIndex = data.readUnsignedShort();
					return utf8[sourceFileIndex];
				}
				data.skipBytes((int) length);
			}
		}
		String simpleName = classFile.getFileName().toString();
		int dollar = simpleName.indexOf('$');
		if (dollar >= 0) {
			simpleName = simpleName.substring(0, dollar);
		}
		return simpleName.substring(0, simpleName.length() - ".class".length()) + ".java (no SourceFile attribute)";
	}

	private static void skipMembers(DataInputStream data) throws IOException {
		int count = data.readUnsignedShort();
		for (int i = 0; i < count; i++) {
			data.skipBytes(2); // access_flags
			data.skipBytes(2); // name_index
			data.skipBytes(2); // descriptor_index
			int attributesCount = data.readUnsignedShort();
			for (int a = 0; a < attributesCount; a++) {
				data.skipBytes(2); // attribute_name_index
				long length = data.readInt() & 0xFFFFFFFFL;
				data.skipBytes((int) length);
			}
		}
	}

	private static final class Component {

		final String packageName;

		final String sourceFile;

		long bytes;

		int classFiles;

		Component(String packageName, String sourceFile) {
			this.packageName = packageName;
			this.sourceFile = sourceFile;
		}

		String label() {
			String simple = sourceFile.endsWith(".java")
					? sourceFile.substring(0, sourceFile.length() - ".java".length()) : sourceFile;
			return packageName.isEmpty() ? simple : packageName + "." + simple;
		}

	}

}
