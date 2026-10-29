package io.jstach.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleasePlanTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
			# tag   | pom             | release | next  | force | pom after the release
			0.16.3  | 0.17.0-SNAPSHOT | 0.16.4  | -     | false | 0.17.0-SNAPSHOT
			0.16.3  | 0.17.0-SNAPSHOT | 0.17.0  | -     | false | 0.18.0-SNAPSHOT
			0.16.3  | 0.17.0-SNAPSHOT | 0.17.0  | 1.0.0 | false | 1.0.0-SNAPSHOT
			0.16.3  | 0.17.0-SNAPSHOT | 0.16.4  | 1.0.0 | false | 1.0.0-SNAPSHOT
			0.16.3  | 1.0.0-SNAPSHOT  | 0.16.4  | -     | false | 1.0.0-SNAPSHOT
			0.16.3  | 1.0.0-SNAPSHOT  | 0.17.0  | -     | false | 1.0.0-SNAPSHOT
			0.16.3  | 1.0.0-SNAPSHOT  | 1.0.0   | -     | false | 1.1.0-SNAPSHOT
			1.0.0   | 1.1.0-SNAPSHOT  | 1.0.1   | -     | false | 1.1.0-SNAPSHOT
			1.0.0   | 1.1.0-SNAPSHOT  | 1.1.0   | 2.0.0 | true  | 2.0.0-SNAPSHOT
			-       | 0.1.0-SNAPSHOT  | 0.1.0   | -     | false | 0.2.0-SNAPSHOT
			""")
	void allowed(String tag, String pom, String release, String next, boolean force, String expected) {
		var plan = ReleasePlan.of(Version.of(release), tag == null ? null : Version.of(tag), Version.of(pom),
				next == null ? null : Version.of(next), force);
		assertEquals(Version.of(release), plan.release());
		assertEquals(expected, plan.next().print(Version.PrintFlag.SNAPSHOT));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
			# tag   | pom             | release        | next   | force | message
			0.16.3  | 0.17.0-SNAPSHOT | 0.16.5         | -      | false | 0.16.5 does not follow the newest tag v0.16.3. Use 0.16.4 or 0.17.0 or 1.0.0, or --force.
			0.16.3  | 0.17.0-SNAPSHOT | 0.17.0-SNAPSHOT| -      | false | The release cannot be a SNAPSHOT: 0.17.0-SNAPSHOT
			0.16.3  | 0.17.0          | 0.16.4         | -      | false | The pom should be a -SNAPSHOT version: 0.17.0
			0.16.3  | 0.18.0-SNAPSHOT | 0.16.4         | -      | false | The pom after 0.16.4 would be 0.18.0-SNAPSHOT. Use 0.17.0-SNAPSHOT or 1.0.0-SNAPSHOT.
			0.16.3  | 0.17.0-SNAPSHOT | 0.17.0         | 0.19.0 | false | The pom after 0.17.0 would be 0.19.0-SNAPSHOT. Use 0.18.0-SNAPSHOT or 1.0.0-SNAPSHOT.
			0.16.3  | 0.17.0-SNAPSHOT | 0.17.0         | 0.18.1 | false | The pom after 0.17.0 would be 0.18.1-SNAPSHOT. Use 0.18.0-SNAPSHOT or 1.0.0-SNAPSHOT.
			0.16.3  | 0.18.0-SNAPSHOT | 0.17.0         | -      | false | 0.17.0 is a minor or major release, so it must be the version the pom is building: 0.18.0-SNAPSHOT. Use --force to release it anyway.
			1.0.0   | 1.1.0-SNAPSHOT  | 1.1.0          | 2.0.0  | false | The pom after 1.1.0 would be 2.0.0-SNAPSHOT, a new major version, which needs --force.
			1.0.0   | 2.0.0-SNAPSHOT  | 1.0.1          | -      | false | The pom after 1.0.1 would be 2.0.0-SNAPSHOT, a new major version, which needs --force.
			1.0.0   | 2.0.0-SNAPSHOT  | 1.1.0          | -      | false | 1.1.0 is a minor or major release, so it must be the version the pom is building: 2.0.0-SNAPSHOT. Use --force to release it anyway.
			1.0.0   | 1.1.0-SNAPSHOT  | 1.1.0          | 1.3.0  | true  | The pom after 1.1.0 would be 1.3.0-SNAPSHOT. Use 1.2.0-SNAPSHOT, or 2.0.0-SNAPSHOT with --force.
			""")
	void rejected(String tag, String pom, String release, String next, boolean force, String message) {
		var e = assertThrows(IllegalArgumentException.class,
				() -> ReleasePlan.of(Version.of(release), tag == null ? null : Version.of(tag), Version.of(pom),
						next == null ? null : Version.of(next), force));
		assertEquals(message, e.getMessage());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			0.17.0-SNAPSHOT | 0.17.0          | -1
			0.17.0-SNAPSHOT | 0.16.3          | 1
			0.17.0          | 0.17.0-SNAPSHOT | 1
			1.0.0-SNAPSHOT  | 0.17.0          | 1
			""")
	void snapshotSortsBeforeItsRelease(String a, String b, int expected) {
		assertEquals(expected, Integer.signum(Version.of(a).compareTo(Version.of(b))));
	}

}
