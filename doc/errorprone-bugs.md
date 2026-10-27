# Error Prone bugs encountered

Bugs hit while running this project's `errorprone` analysis profile, kept here so they
can be filed against the Error Prone issue tracker
(https://github.com/google/error-prone/issues). Each entry has a minimal repro so it is
easy to turn into a standalone bug report.

## `InvalidSnippet` flags external snippets that use `class`

Status: **not yet filed**.

- **Error Prone version seen:** 2.51.0. 2.50.0 did not report it.
- **Command:** `bin/analyze.sh errorprone` (the build uses `-Werror`, so the warning
  fails it).
- **Symptom:**
  ```
  [InvalidSnippet] This @snippet has no body. Is it meant to contain inline code?
  Snippets should start with "{@snippet :" followed by a newline.
  ```
- **Trigger:** an external snippet that names its source with the `class` attribute:
  ```java
  /**
   * {@snippet class = "snippets.DecoratorExample" region = "decoratorExample"}
   */
  public interface Example {}
  ```
  The javadoc spec allows either `class` or `file` for an external snippet. The check's
  `visitSnippet` skips a snippet with no body only when it has a `file` attribute, so a
  `class` snippet is reported.
- **Not affected:** `file` snippets, hybrid snippets (`class` plus an inline body, as in
  `RainbowGum`), and snippets in `package-info.java`, which the check does not visit.
- **Workaround:** `LoggerDecoratorService` uses
  `{@snippet file = "snippets/DecoratorExample.java" region = "decoratorExample"}`,
  which resolves through the same `--snippet-path`. Once fixed upstream it can go back to
  `class`.
