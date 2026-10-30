# Error Prone bugs encountered

Bugs hit while running this project's `errorprone` analysis profile, kept here so they
can be filed against the Error Prone issue tracker
(https://github.com/google/error-prone/issues). Each entry has a minimal repro so it is
easy to turn into a standalone bug report.

## `InvalidInlineTag` rejects `{@index}`, `{@summary}` and `{@systemProperty}`

Status: **already reported** as https://github.com/google/error-prone/issues/3913
("Missing standard Javadoc tags", open since 2023-05-16). That issue is about `@hidden`,
and a comment adds `{@summary}`; `{@index}` and `{@systemProperty}` are not mentioned
yet. A maintainer replied there that a PR adding the missing standard tags is welcome.
Add a comment with the repro below, or send that PR, rather than filing a new issue.

- **Error Prone versions confirmed on:** 2.50.0 (this project's version) and 2.51.0,
  the latest release as of 2026-10-30. `JavadocTag.java` on Error Prone's `master`
  branch, checked the same day, does not list these tags either, so no fix is pending.
- **JDK:** `javac 27` with `--release 21`.
- **Symptom:** every use of one of these standard javadoc inline tags is reported, on a
  class, a field, or a method:
  ```
  [InvalidInlineTag] The tag @index is not allowed on this type of element.
  [InvalidInlineTag] The tag @summary is not allowed on this type of element.
  [InvalidInlineTag] The tag @systemProperty is not allowed on this type of element.
  ```
  The message says the tag is in the wrong place, but the tags are allowed in any doc
  comment. `javadoc -Xdoclint:all` accepts the same file, and javadoc adds `{@index}`
  and `{@systemProperty}` terms to the search index as it should.
- **Cause:** `InvalidInlineTag` accepts a tag only if it is in
  `JavadocTag.VALID_CLASS_TAGS`, `VALID_VARIABLE_TAGS` or `VALID_METHOD_TAGS`
  (`core/src/main/java/com/google/errorprone/bugpatterns/javadoc/JavadocTag.java`).
  Their common inline tags are `code`, `docRoot`, `link`, `linkplain`, `literal` and
  `snippet`, plus `inheritDoc` and `value` on some elements. `index` (JDK 9),
  `summary` (JDK 10) and `systemProperty` (JDK 12) are missing.
- **Suggested fix:** add `inlineTag("index")`, `inlineTag("summary")` and
  `inlineTag("systemProperty")` to `COMMON_TAGS`.
- **Minimal repro:** compile this class with Error Prone using the Maven setup from the
  Error Prone installation docs (`-XDcompilePolicy=simple`,
  `--should-stop=ifError=FLOW`, `-Xplugin:ErrorProne`, and the usual `--add-exports`
  and `--add-opens` for `jdk.compiler`):
  ```java
  package p;

  /**
   * A type. See {@index example.type}.
   */
  public class Example {

  	/**
  	 * A field. Property: {@index example.field}.
  	 */
  	public static final String FIELD = "example.field";

  	/**
  	 * {@summary A method.} See {@index example.method a description}.
  	 */
  	public void method() {
  	}

  	/**
  	 * Another field. Property: {@systemProperty example.system}.
  	 */
  	public static final String SYSTEM = "example.system";

  }
  ```
  Output (2.50.0 and 2.51.0 are identical):
  ```
  Example.java:[4,4] [InvalidInlineTag] The tag @index is not allowed on this type of element.
  Example.java:[9,5] [InvalidInlineTag] The tag @index is not allowed on this type of element.
  Example.java:[14,5] [InvalidInlineTag] The tag @summary is not allowed on this type of element.
  Example.java:[14,5] [InvalidInlineTag] The tag @index is not allowed on this type of element.
  Example.java:[20,5] [InvalidInlineTag] The tag @systemProperty is not allowed on this type of element.
  ```
- **Where it hits this project:** property keys are indexed for the javadoc search
  with `{@index}`, in every builder `rainbowgum-apt` generates and on the property
  constants (`LogProperties`, `LogEncoder`, and constants in several modules).
- **Workaround:** the `errorprone` profile passes
  `-XepOpt:Javadoc:customInlineTags=index,summary,systemProperty` (root `pom.xml`, and
  `rainbowgum-slf4j/pom.xml`, which overrides the arguments). The flag was added by
  commit `1e1f293` for issue #1811; 2.50.0 has it, 2.45.0 does not, and the releases
  in between were not checked. With it the repro above compiles clean, and the check
  still reports other unknown tags. Drop the flag once a release knows these tags.
