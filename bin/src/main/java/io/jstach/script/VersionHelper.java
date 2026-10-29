package io.jstach.script;

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/**
 * Version Helper (vh) is to help manage versions on git tags, poms, and properties as
 * well as manage timestamps. It replaces a lot of functionality that the Maven release
 * plugin would do.
 *
 * It should be run in the project root like:
 *
 * <code>
 * bin/vh [COMMAND]
 * </code>
 *
 * @author agentgt
 *
 */
public final class VersionHelper {

	public static void main(String[] args) {
		try {
			run(List.of(args));
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

	}

	static void run(List<String> _args) throws Exception {
		Command command = Command.parseCommand(Command.class, _args, 0);
		List<String> params = _args.stream().skip(1).toList();
		command.run(params);
	}

}

interface HelpSupport {

	String desc();

}

enum Command implements HelpSupport {

	RELEASE("Commits, tags, and pushes a release on its own branch, then sets the poms for the deploy: "
			+ "'release <VERSION> [--next <VERSION>] [--force]'") {
		@Override
		public void run(List<String> args) throws IOException {
			Version release = null;
			Version next = null;
			boolean force = false;
			for (int i = 0; i < args.size(); i++) {
				String arg = args.get(i);
				if (arg.equals("--force")) {
					force = true;
				}
				else if (arg.equals("--next") && i + 1 < args.size()) {
					next = Version.of(args.get(++i));
				}
				else if (release == null && !arg.startsWith("-")) {
					release = Version.of(arg);
				}
				else {
					throw new IllegalArgumentException(
							"Bad argument: " + arg + ". Use: release <VERSION> [--next <VERSION>] [--force]");
				}
			}
			if (release == null) {
				throw new IllegalArgumentException("Missing version. Use: release <VERSION> [--next <VERSION>] [--force]");
			}
			out.println("Validating git");
			validateGit();
			execute("git fetch " + REMOTE);
			validateOnMain();
			String tag = release.tag();
			String branch = RELEASE_BRANCH_PREFIX + tag;
			validateNotOnRemote(tag, branch);
			out.println("Validating versions");
			var pom = pom();
			var plan = ReleasePlan.of(release, newestTagOrNull(), pom, next, force);
			out.println("Release: " + plan.release().print());
			out.println("POM after the release: " + plan.next().label());

			long timestamp = Instant.now().getEpochSecond();
			current(plan.release(), timestamp);
			if (!plan.next().equals(pom)) {
				setPoms(pom, plan.next());
			}
			interactive("git", "switch", "-c", branch);
			interactive("git", "add", "-u");
			interactive("git", "commit", "-m", "Release " + tag);
			tag(plan.release());
			interactive("git", "push", "--atomic", REMOTE, branch, "refs/tags/" + tag);

			out.println("Setting POM to release version and updating timestamp");
			pom(plan.release(), timestamp);
			out.println();
			out.println(branch + " and " + tag + " are pushed. Do not commit the altered poms.");
			out.println("Now run the deploy:");
			out.println();
			out.println("./mvnw clean deploy -T1 -Duser.timezone=UTC -Ddeploy=release");
			out.println();
			out.println("Once it succeeds, run:");
			out.println();
			out.println("bin/vh finish");
		}
	},
	FINISH("After the deploy, restores the poms and fast-forwards main to the release branch.") {
		@Override
		public void run(List<String> args) throws IOException {
			var current = current();
			String tag = current.tag();
			String branch = RELEASE_BRANCH_PREFIX + tag;
			String head = execute("git rev-parse --abbrev-ref HEAD").trim();
			if (!head.equals(branch)) {
				throw new RuntimeException("Not on " + branch + " (from version.properties). Current branch: " + head);
			}
			var changed = execute("git status --porcelain=v1 --untracked-files=no").lines()
				.map(line -> line.substring(3))
				.toList();
			for (String file : changed) {
				if (!file.equals("pom.xml") && !file.endsWith("/pom.xml")) {
					throw new RuntimeException("Only the release poms should be changed. Changed: " + file);
				}
			}
			if (!changed.isEmpty()) {
				out.println("Restoring the poms");
				var restore = new ArrayList<>(List.of("git", "checkout", "--"));
				restore.addAll(changed);
				interactive(restore.toArray(String[]::new));
			}
			String tagCommit = execute("git rev-parse " + tag + "^{commit}").trim();
			String headCommit = execute("git rev-parse HEAD").trim();
			if (!tagCommit.equals(headCommit)) {
				throw new RuntimeException(tag + " is not at HEAD of " + branch
						+ ". If a fix was committed for the deploy, move the tag first (see release.md).");
			}
			try {
				interactive("git", "push", REMOTE, branch + ":" + MAIN_BRANCH);
			}
			catch (IOException e) {
				throw new RuntimeException(MAIN_BRANCH + " could not be fast-forwarded to " + branch
						+ ", probably because it moved since the release started. " + tag
						+ " is already pushed, so either merge " + branch + " into " + MAIN_BRANCH
						+ " yourself or leave it unmerged.", e);
			}
			interactive("git", "switch", MAIN_BRANCH);
			interactive("git", "merge", "--ff-only", branch);
			out.println();
			out.println(MAIN_BRANCH + " is at " + tag + ". " + branch + " is no longer needed. To delete it:");
			out.println();
			out.println("git branch -d " + branch + " && git push " + REMOTE + " --delete " + branch);
		}
	},
	CURRENT("Prints the current version based on version.properties.") {
		@Override
		public void run(List<String> args) throws IOException {
			var version = current();
			out.println(version.print());
		}
	},
	VALIDATE("Validates that version.properties, git tag, and pom are specified correctly.") {
		@Override
		public void run(List<String> args) throws IOException {
			validate(current(), tag(), pom());
		}
	},
	PARSE("Parses a version: 'parse <VERSION>'") {
		@Override
		public void run(List<String> args) throws IOException {
			var a = Version.of(args.get(0));
			out.println(a.print());
		}
	},
	GET("Gets a version string from a source: 'get <SOURCE>'") {
		@Override
		public void run(List<String> args) throws IOException {
			VersionSource getCmd = Command.parseCommand(VersionSource.class, args, 0, this);
			switch (getCmd) {
				case CURRENT -> {
					var version = current();
					out.println(version.print());
				}
				case POM -> {
					var v = pom();
					out.println(v.label());
				}
				case TAG -> {
					var v = tag();
					out.println(v.label());
				}
			}

		}
	},
	SET("Sets a version string on a source: 'set <SOURCE>'") {
		@Override
		public void run(List<String> args) throws IOException {
			VersionSource setCmd = Command.parseCommand(VersionSource.class, args, 0, this);
			List<String> params = args.stream().skip(1).toList();
			Version v;
			long timestamp;
			if (params.isEmpty()) {
				v = current();
				timestamp = timestamp();
			}
			else {
				v = Version.of(params.get(0));
				timestamp = Instant.now().getEpochSecond();
			}
			switch (setCmd) {
				case CURRENT -> {
					current(v, timestamp);
				}
				case POM -> {
					pom(v, timestamp);
				}
				case TAG -> {
					tag(v);
				}
			}
		}

	};

	public static final String VERSION_PROPERTIES = "version.properties";

	/**
	 * Extra, out-of-reactor project directories: each has its own {@code pom.xml} with a
	 * {@code <parent>} pointing back at this project's parent pom, but is not reachable
	 * via any {@code <module>} in the main reactor (usually because it needs its own
	 * special build setup, e.g. a GraalVM native-image or JDK AOT cache smoke test), so
	 * a plain {@code mvn versions:set} run from the project root never touches it. Edit
	 * this list for whichever directories apply when copying this script to a different
	 * project; leave it {@code List.of()} if there are none.
	 */
	public static final List<String> EXTRA_POM_DIRECTORIES = List.of("test/rainbowgum-test-native",
			"test/rainbowgum-test-aot-cache");

	/**
	 * Project directories whose {@code pom.xml} declares this project's version in a
	 * {@value #VERSION_POM_PROPERTY} property instead of inheriting it, because they are
	 * written like an application using the project (for example an example app with its
	 * own framework parent). {@code mvn versions:set} does not update properties, so it
	 * is rewritten directly, along with the pom's own {@code <version>}, which matches
	 * the project version so reactor builds do not print a version per module. Leave it
	 * {@code List.of()} if there are none.
	 */
	public static final List<String> VERSION_PROPERTY_POM_DIRECTORIES = List.of("examples/helidon",
			"examples/micronaut", "examples/simple-props");

	/**
	 * The property {@link #VERSION_PROPERTY_POM_DIRECTORIES} poms declare the version in.
	 */
	public static final String VERSION_POM_PROPERTY = "rainbowgum.version";

	/**
	 * The git remote releases are pushed to.
	 */
	public static final String REMOTE = "origin";

	/**
	 * The branch releases are made from and fast-forwarded to after the deploy.
	 */
	public static final String MAIN_BRANCH = "main";

	/**
	 * Prefix of the branch a release is committed and tagged on, followed by the tag.
	 */
	public static final String RELEASE_BRANCH_PREFIX = "release/";

	private final String desc;

	Command(String desc) {
		this.desc = desc;
	}

	public String desc() {
		return this.desc;
	}

	public abstract void run(List<String> args) throws IOException;

	enum VersionSource implements HelpSupport {

		CURRENT(VERSION_PROPERTIES), POM("pom.xml"), TAG("git tag");

		private final String desc;

		VersionSource(String desc) {
			this.desc = desc;
		}

		public String desc() {
			return this.desc;
		}

	}

	static void validate(Version current, Version tag, Version pom) throws IOException {

		if (!tag.equals(current)) {
			throw new RuntimeException("version mismatch. current = " + current.print() + " tag = " + tag.print());
		}
		validatePom(pom, current);
	}

	static void validatePom(Version pom, Version current) {
		if (pom.compareTo(current) <= 0) {
			throw new RuntimeException("pom version is not greater than current version.properties. current = "
					+ current.print() + " pom = " + pom);
		}
		if (!pom.snapshot()) {
			throw new RuntimeException("pom should be a -SNAPSHOT version");
		}
	}

	static Version current() throws IOException {
		var props = new Properties();
		try (var r = Files.newBufferedReader(Path.of(VERSION_PROPERTIES))) {
			props.load(r);
		}
		String v = props.getProperty("version");
		return Version.of(v);
	}

	static void current(Version v, long timestamp) throws IOException {
		if (v.snapshot()) {
			throw new IllegalArgumentException("version.properties cannot have SNAPSHOT versions. version: "
					+ v.print(Version.PrintFlag.SNAPSHOT));
		}
		try (var br = Files.newBufferedWriter(Path.of(VERSION_PROPERTIES), StandardCharsets.ISO_8859_1)) {
			writeProperties(br, Map.entry("version", v.print()), Map.entry("timestamp", "" + timestamp));
			br.flush();
		}
	}

	static long timestamp() throws IOException {
		var props = new Properties();
		try (var r = Files.newBufferedReader(Path.of(VERSION_PROPERTIES))) {
			props.load(r);
		}
		String t = props.getProperty("timestamp");
		if (t == null) {
			return -1;
		}
		return Long.parseLong(t);
	}

	static Version pom() throws IOException {
		return Version.of(readPom(Path.of("pom.xml")).projectVersion());
	}

	/*
	 * Sets every pom to the version and the root pom's timestamp, without running Maven.
	 */
	static void pom(Version version, long timestamp) throws IOException {
		setPoms(pom(), version);
		updatePomProperty(Path.of("pom.xml"), "project.build.outputTimestamp", "" + timestamp);
	}

	/**
	 * Rewrites the version in every pom of the project from one version to another as
	 * text, leaving the rest of each file byte for byte unchanged: each reactor module
	 * (including those only listed in profiles), the {@link #EXTRA_POM_DIRECTORIES} parent
	 * versions, and the {@link #VERSION_PROPERTY_POM_DIRECTORIES} property. Fails if any of
	 * them is not on the from version.
	 * @param from the version the poms are on.
	 * @param to the new version.
	 */
	static void setPoms(Version from, Version to) throws IOException {
		Set<Path> poms = new LinkedHashSet<>();
		collectModulePoms(Path.of("pom.xml"), poms);
		for (String dir : EXTRA_POM_DIRECTORIES) {
			poms.add(Path.of(dir, "pom.xml"));
		}
		for (String dir : VERSION_PROPERTY_POM_DIRECTORIES) {
			poms.add(Path.of(dir, "pom.xml"));
		}
		Map<Path, PomVersions> versions = new LinkedHashMap<>();
		for (Path pom : poms) {
			versions.put(pom, readPom(pom));
		}
		Set<String> projectPoms = new HashSet<>();
		for (var v : versions.values()) {
			projectPoms.add(v.groupId() + ":" + v.artifactId());
		}
		Map<Path, String> updated = new LinkedHashMap<>();
		for (var e : versions.entrySet()) {
			updated.put(e.getKey(), setPomVersion(e.getKey(), e.getValue(), projectPoms, from, to));
		}
		for (var e : updated.entrySet()) {
			Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8);
		}
	}

	static void collectModulePoms(Path pom, Set<Path> poms) throws IOException {
		if (!poms.add(pom.normalize())) {
			return;
		}
		Path dir = pom.toAbsolutePath().getParent();
		Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(Files.readString(pom, StandardCharsets.UTF_8));
		while (m.find()) {
			Path module = Path.of("").toAbsolutePath().relativize(dir.resolve(m.group(1).trim()).resolve("pom.xml"));
			collectModulePoms(module, poms);
		}
	}

	record PomVersions(String groupId, String artifactId, String parent, String parentVersion, String projectVersion,
			String versionProperty) {
	}

	static PomVersions readPom(Path pomFile) throws IOException {
		try (InputStream is = Files.newInputStream(pomFile, StandardOpenOption.READ)) {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			XPath xpath = XPathFactory.newInstance().newXPath();
			String groupId = xpath.evaluate("/project/groupId", doc);
			String parentGroupId = xpath.evaluate("/project/parent/groupId", doc);
			return new PomVersions(groupId.isEmpty() ? parentGroupId : groupId, xpath.evaluate("/project/artifactId", doc),
					parentGroupId + ":" + xpath.evaluate("/project/parent/artifactId", doc),
					xpath.evaluate("/project/parent/version", doc), xpath.evaluate("/project/version", doc),
					xpath.evaluate("/project/properties/" + VERSION_POM_PROPERTY, doc));
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("Could not read " + pomFile, e);
		}
	}

	/*
	 * The pom's text with its parent version (when the parent is one of the project's
	 * poms), own version, and version property changed from one version to the other.
	 */
	static String setPomVersion(Path pomFile, PomVersions versions, Set<String> projectPoms, Version from, Version to)
			throws IOException {
		String fromLabel = from.print(Version.PrintFlag.SNAPSHOT);
		String toLabel = to.print(Version.PrintFlag.SNAPSHOT);
		String content = Files.readString(pomFile, StandardCharsets.UTF_8);
		int parentStart = content.indexOf("<parent>");
		int parentEnd = parentStart < 0 ? -1 : content.indexOf("</parent>", parentStart);
		String fromElement = "<version>" + fromLabel + "</version>";
		String toElement = "<version>" + toLabel + "</version>";
		boolean changed = false;
		if (projectPoms.contains(versions.parent())) {
			requireVersion(pomFile, "parent version", versions.parentVersion(), fromLabel);
			int i = content.indexOf(fromElement, parentStart);
			content = content.substring(0, i) + toElement + content.substring(i + fromElement.length());
			changed = true;
		}
		if (!versions.projectVersion().isEmpty()) {
			requireVersion(pomFile, "version", versions.projectVersion(), fromLabel);
			int i = content.indexOf(fromElement);
			if (i > parentStart && i < parentEnd) {
				i = content.indexOf(fromElement, parentEnd);
			}
			content = content.substring(0, i) + toElement + content.substring(i + fromElement.length());
			changed = true;
		}
		if (!versions.versionProperty().isEmpty()) {
			requireVersion(pomFile, VERSION_POM_PROPERTY, versions.versionProperty(), fromLabel);
			content = content.replace("<" + VERSION_POM_PROPERTY + ">" + fromLabel + "</" + VERSION_POM_PROPERTY + ">",
					"<" + VERSION_POM_PROPERTY + ">" + toLabel + "</" + VERSION_POM_PROPERTY + ">");
			changed = true;
		}
		if (!changed) {
			throw new IllegalStateException("No project version found in " + pomFile);
		}
		return content;
	}

	static void requireVersion(Path pomFile, String what, String actual, String expected) {
		if (!actual.equals(expected)) {
			throw new IllegalStateException(pomFile + " has " + what + " " + actual + ", expected " + expected);
		}
	}

	/**
	 * Rewrites the single {@code <name>value</name>} element of a pom property as text,
	 * leaving the rest of the file byte for byte unchanged.
	 * @param pomFile path to the pom.xml.
	 * @param name property name.
	 * @param value new value.
	 */
	static void updatePomProperty(Path pomFile, String name, String value) throws IOException {
		String content = Files.readString(pomFile, StandardCharsets.UTF_8);
		var matcher = Pattern.compile("<" + Pattern.quote(name) + ">[^<]*</" + Pattern.quote(name) + ">")
			.matcher(content);
		if (!matcher.find()) {
			throw new IllegalStateException("No <" + name + "> property found in " + pomFile);
		}
		int start = matcher.start();
		int end = matcher.end();
		if (matcher.find()) {
			throw new IllegalStateException("More than one <" + name + "> property found in " + pomFile);
		}
		String updated = content.substring(0, start) + "<" + name + ">" + value + "</" + name + ">"
				+ content.substring(end);
		Files.writeString(pomFile, updated, StandardCharsets.UTF_8);
	}

	static Version tag() throws IOException {
		String r = execute("git tag -l --sort=-v:refname", 1);
		var v = Version.of(r.trim());
		return v;
	}

	static Version newestTagOrNull() throws IOException {
		String r = execute("git tag -l --sort=-v:refname 'v*'", 1).trim();
		return r.isEmpty() ? null : Version.of(r);
	}

	static void tag(Version version) throws IOException {
		if (version.snapshot()) {
			throw new IllegalArgumentException(
					"tag cannot have SNAPSHOT versions. version: " + version.print(Version.PrintFlag.SNAPSHOT));
		}
		String command = "git tag -a -m 'release " + version.print() + "'" + " v" + version.print();
		out.println("Executing " + command);
		String r = execute(command, -1);
		out.println(version);
		out.println(r);
	}

	static void validateGit() throws IOException {
		String command = "git status --porcelain=v1 2>/dev/null";
		String r = execute(command, -1);
		if (!r.trim().isBlank()) {
			throw new RuntimeException("Git has changed files: \n" + r);
		}
	}

	static void validateOnMain() throws IOException {
		String branch = execute("git rev-parse --abbrev-ref HEAD").trim();
		if (!branch.equals(MAIN_BRANCH)) {
			throw new RuntimeException("Releases are made from " + MAIN_BRANCH + ". Current branch: " + branch);
		}
		String head = execute("git rev-parse HEAD").trim();
		String remote = execute("git rev-parse " + REMOTE + "/" + MAIN_BRANCH).trim();
		if (!head.equals(remote)) {
			throw new RuntimeException(MAIN_BRANCH + " is not the same as " + REMOTE + "/" + MAIN_BRANCH
					+ ". Pull or push first.");
		}
	}

	static void validateNotOnRemote(String tag, String branch) throws IOException {
		if (!execute("git tag -l " + tag).isBlank() || !execute("git ls-remote --tags " + REMOTE + " refs/tags/" + tag).isBlank()) {
			throw new RuntimeException("Tag " + tag + " already exists");
		}
		if (!execute("git branch --list " + branch).isBlank()
				|| !execute("git ls-remote --heads " + REMOTE + " refs/heads/" + branch).isBlank()) {
			throw new RuntimeException("Branch " + branch + " already exists");
		}
	}

	/*
	 * Runs a command attached to this terminal, so prompts (for example a passphrase) and
	 * colors work as if it were typed.
	 */
	static void interactive(String... command) throws IOException {
		out.println("Executing " + String.join(" ", command));
		out.flush();
		Process process = new ProcessBuilder(command).inheritIO().start();
		int e;
		try {
			e = process.waitFor();
		}
		catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e1);
		}
		if (e != 0) {
			throw new IOException("Failure executing command: " + String.join(" ", command) + ", exit: " + e);
		}
	}

	static String execute(String command) throws IOException {
		return execute(command, -1);
	}

	static String execute(String command, int lines) throws IOException {
		StringBuilder out = new StringBuilder();
		StringBuilder err = new StringBuilder();
		int e = execute(command, out, err, lines);
		if (e == 0) {
			return out.toString();
		}
		else {
			throw new IOException("Failure executing command: " + command + ", exit: " + e + "\nstdout: "
					+ out.toString() + "\nstderr: " + err.toString());
		}
	}

	static int execute(String command, Appendable out, Appendable err, int lines) throws IOException {
		ProcessBuilder b = new ProcessBuilder();
		b.command("bash", "-c", command);

		Process process = b.start();

		BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

		if (lines < 0) {
			lines = Integer.MAX_VALUE;
		}
		int e;
		try {
			e = process.waitFor();
			append(out, stdout, lines);
			append(err, stderr, -1);
		}
		catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e1);
		}
		return e;
	}

	private static void append(Appendable output, BufferedReader reader, int lines) throws IOException {
		String line;
		int i = 0;
		while ((line = reader.readLine()) != null) {
			if (i >= lines) {
				break;
			}
			output.append(line + "\n");
			i++;
		}
	}

	public static <E extends Enum<E> & HelpSupport> E parseCommand(Class<E> commandType, List<String> args, int index) {
		return parseCommand(commandType, args, index, null);
	}

	public static <E extends Enum<E> & HelpSupport, P extends Enum<P> & HelpSupport> E parseCommand(
			Class<E> commandType, List<String> args, int index, P parent) {
		if (args.size() < (index + 1)) {
			String message;
			if (parent != null) {
				message = "Missing subcommand for '" + parent.name().toLowerCase() + "'. pick: ";
			}
			else {
				message = "Missing command. pick: ";
			}
			message = message + printCommands(commandType);
			throw new RuntimeException(message);
		}
		String arg = args.get(index);

		try {
			if (arg.equalsIgnoreCase("help")) {
				out.println("");

				String message;
				if (parent != null) {
					out.println(parent.desc());
					out.println();
					message = "Sub Commands for '" + parent.name().toLowerCase() + "': ";
				}
				else {
					out.println("""
							Version Helper (vh) is to help manage versions on git tags, poms, and properties
							as well as manage timestamps. It replaces a lot of functionality that the
							Maven release plugin would do.

							It should be run in the project root like:

								bin/vh [COMMAND]

							""");
					message = "Commands: ";
				}
				out.println(message + printCommands(commandType));
				out.println(helpCommands(commandType));
				System.exit(1);
				throw new RuntimeException();
			}
			return Enum.valueOf(commandType, arg.toUpperCase());

		}
		catch (Exception exception) {
			String message = "Bad command. " + arg + " pick: " + printCommands(commandType);
			throw new RuntimeException(message);
		}
	}

	public static <E extends Enum<E>> String printCommands(Class<E> commandType) {
		return ""
				+ Stream.concat(Stream.of("help"), EnumSet.allOf(commandType).stream().map(e -> e.name().toLowerCase()))
						.toList();
	}

	public static <E extends Enum<E> & HelpSupport> String helpCommands(Class<E> commandType) {
		StringBuilder sb = new StringBuilder();
		for (var e : EnumSet.allOf(commandType)) {
			sb.append("\n\t");
			sb.append(e.name().toLowerCase()).append(" - ").append(e.desc());
		}
		sb.append("\n\t");
		sb.append("help - describe commands: '[<COMMAND>] help'");
		sb.append("\n");
		return sb.toString();
	}

	@SafeVarargs
	static void writeProperties(Appendable sb, Entry<String, String>... kvs) throws IOException {
		Map<String, String> m = new LinkedHashMap<>();
		for (var kv : kvs) {
			m.put(kv.getKey(), kv.getValue());
		}
		writeProperties(m, sb);
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "serial" })
	static void writeProperties(Map<String, String> map, Appendable sb) throws IOException {
		StringWriter sw = new StringWriter();
		new Properties() {
			public java.util.Enumeration keys() {
				return Collections.enumeration(map.keySet());
			}

			public java.util.Set entrySet() {
				return map.entrySet();
			};

			public Object get(Object key) {
				return map.get(key);
			};
		}.store(sw, null);
		LineNumberReader lr = new LineNumberReader(new StringReader(sw.toString()));

		String line;
		while ((line = lr.readLine()) != null) {
			if (!line.startsWith("#")) {
				sb.append(line).append("\n");
				out.append(line).append("\n");
			}
		}
	}

}

/**
 * The versions of a release: the release itself and the pom snapshot after it.
 * <p>
 * The release must follow the newest tag as its next patch, minor, or major version. A
 * minor or major release must be the version the pom is building. The pom after the
 * release must be the next minor snapshot, or <code>1.0.0-SNAPSHOT</code> while before
 * 1.0. <code>--force</code> skips the first two rules and also allows the next major
 * snapshot.
 *
 * @param release the release.
 * @param next the pom version after the release.
 */
record ReleasePlan(Version release, Version next) {

	static final Version ONE = new Version(1, 0, 0, true);

	/**
	 * Validates the release and works out the pom after it.
	 * @param release version to release.
	 * @param newestTag the newest release tag, or null if there is none.
	 * @param pom the pom version the release starts from.
	 * @param next the requested pom version after the release, or null for the default.
	 * @param force whether to skip the tag and pom checks and allow the next major.
	 * @return plan.
	 * @throws IllegalArgumentException if a rule is broken.
	 */
	static ReleasePlan of(Version release, Version newestTag, Version pom, Version next, boolean force) {
		if (release.snapshot()) {
			throw new IllegalArgumentException("The release cannot be a SNAPSHOT: " + release.print(Version.PrintFlag.SNAPSHOT));
		}
		if (!pom.snapshot()) {
			throw new IllegalArgumentException("The pom should be a -SNAPSHOT version: " + pom.print(Version.PrintFlag.SNAPSHOT));
		}
		if (newestTag != null && !force) {
			var allowed = List.of(newestTag.nextPatch(), newestTag.nextMinor(), newestTag.nextMajor());
			if (!allowed.contains(release)) {
				throw new IllegalArgumentException(release.print() + " does not follow the newest tag "
						+ newestTag.tag() + ". Use " + printAll(allowed, false) + ", or --force.");
			}
		}
		boolean patch = release.patch() > 0;
		boolean releasesPom = release.equals(pom.release());
		if (!patch && !releasesPom && !force && !(release.major() == 0 && pom.equals(ONE))) {
			throw new IllegalArgumentException(release.print()
					+ " is a minor or major release, so it must be the version the pom is building: " + pom.print(Version.PrintFlag.SNAPSHOT)
					+ ". Use --force to release it anyway.");
		}
		Version resolvedNext;
		if (next != null) {
			resolvedNext = next.toSnapshot();
		}
		else if (!patch && releasesPom) {
			resolvedNext = release.nextMinor().toSnapshot();
		}
		else {
			resolvedNext = pom;
		}
		var allowed = new ArrayList<Version>();
		allowed.add(release.nextMinor().toSnapshot());
		if (release.major() == 0) {
			allowed.add(ONE);
		}
		if (!allowed.contains(resolvedNext)) {
			var major = release.nextMajor().toSnapshot();
			if (resolvedNext.equals(major) && !force) {
				throw new IllegalArgumentException("The pom after " + release.print() + " would be " + major.print(Version.PrintFlag.SNAPSHOT)
						+ ", a new major version, which needs --force.");
			}
			if (!resolvedNext.equals(major)) {
				throw new IllegalArgumentException("The pom after " + release.print() + " would be "
						+ resolvedNext.print(Version.PrintFlag.SNAPSHOT) + ". Use " + printAll(allowed, true)
						+ (release.major() == 0 ? "" : ", or " + major.print(Version.PrintFlag.SNAPSHOT) + " with --force") + ".");
			}
		}
		return new ReleasePlan(release, resolvedNext);
	}

	private static String printAll(List<Version> versions, boolean snapshot) {
		return String.join(" or ", versions.stream()
			.map(v -> snapshot ? v.print(Version.PrintFlag.SNAPSHOT) : v.print())
			.toList());
	}

}

record Version(int major, int minor, int patch, boolean snapshot) implements Comparable<Version> {

	static final Pattern pattern = Pattern.compile("v?([0-9]+).([0-9]+).([0-9]+)(-SNAPSHOT)?");
	static final Comparator<Version> COMPARATOR = Comparator.comparingInt(Version::major)
			.thenComparingInt(Version::minor).thenComparingInt(Version::patch).thenComparing(v -> !v.snapshot());

	static Version of(String s) {
		Matcher m = pattern.matcher(s);
		if (!m.matches()) {
			throw new IllegalArgumentException("bad version: " + s);
		}
		int major = Integer.parseInt(m.group(1));
		int minor = Integer.parseInt(m.group(2));
		int patch = Integer.parseInt(m.group(3));
		boolean snapshot = false;
		if (m.groupCount() > 3 && "-SNAPSHOT".equals(m.group(4))) {
			snapshot = true;
		}
		return new Version(major, minor, patch, snapshot);
	}

	enum PrintFlag {

		PREFIX, SNAPSHOT

	}

	public String print(Set<PrintFlag> flags) {
		return (flags.contains(PrintFlag.PREFIX) ? "v" : "") + major() + "." + minor() + "." + patch()
				+ (flags.contains(PrintFlag.SNAPSHOT) && snapshot ? "-SNAPSHOT" : "");
	}

	public String print() {
		return print(Set.of());
	}

	public String print(PrintFlag flag, PrintFlag... flags) {
		Set<PrintFlag> fs = EnumSet.of(flag, flags);
		return print(fs);
	}

	public String label() {
		return print(Set.of(PrintFlag.PREFIX, PrintFlag.SNAPSHOT));
	}

	public String tag() {
		return "v" + print();
	}

	Version release() {
		return new Version(major, minor, patch, false);
	}

	Version toSnapshot() {
		return new Version(major, minor, patch, true);
	}

	Version nextPatch() {
		return new Version(major, minor, patch + 1, false);
	}

	Version nextMinor() {
		return new Version(major, minor + 1, 0, false);
	}

	Version nextMajor() {
		return new Version(major + 1, 0, 0, false);
	}

	public String toString() {
		return label();
	}

	void validate(Version b) {
		if (this.equals(b)) {
			out.println(b.print());
		}
		else {
			throw new IllegalArgumentException("version mismatch. a = " + this.label() + " b = " + b.label());
		}
	}

	@Override
	public int compareTo(Version o) {
		return COMPARATOR.compare(this, o);
	}

}
