# AGENTS.md

Build and workflow practices for this repository, written for an AI coding agent
(or a new contributor) to get productive quickly. Adjust module names/paths for
your project — the shape (multi-module Maven reactor, JPMS, static analysis
profiles, Spring-style formatting) is the part worth reusing as-is.

## Repo shape

- Multi-module Maven **reactor** build: a root `pom.xml` (packaging `pom`) declares
  shared `dependencyManagement`/`pluginManagement` and a set of build **profiles**;
  every leaf module is a real JPMS module with its own `module-info.java`.
- Each leaf module's `pom.xml` sets a `parent.root` property pointing back at the
  repo root, scoped to *that module's own depth* — e.g. a module directly under
  root uses `<parent.root>${basedir}/..</parent.root>`, a module two levels down
  uses `${basedir}/../..`, etc. This is how each module locates root-level shared
  resources (javadoc stylesheet, snippet source dirs) regardless of nesting depth.
  **Never copy another module's `parent.root` value** — it must match that
  module's own directory depth.
- Modules that are deliberately near-duplicates of each other (e.g. one variant
  per major version of an integration you support, like a "vendor-v3" and
  "vendor-v4" module) are **not** refactored to share code. Each carries its own
  copy of the logic, with a comment in `module-info.java` explaining why: no
  consumer ever imports these packages directly (they're wired up via
  `provides`/`uses` or a service-loader-style descriptor), so there's no
  "drop-in" benefit to deduplicating, and it keeps each variant free to diverge
  when the thing it integrates with changes incompatibly between versions.

## Building

Always use the Maven wrapper (`./mvnw`), never a system-installed `mvn` — it pins
the exact Maven version for reproducible builds.

```bash
# Full reactor build, all modules, all tests
./mvnw -q clean install

# Just one module (and whatever it depends on, with -am)
./mvnw -q -pl core -am install

# Skip tests when you just need compilation feedback fast
./mvnw -q -pl core -am install -DskipTests
```

### Known quirk: annotation-processor module + moditect

If any module packages a jar that later has a `module-info.class` injected by
the **moditect** plugin (common for an annotation-processor module that must
ship both an APT jar *and* a real JPMS module descriptor), re-running
`package`/`install` on that module **without a clean first** fails with:

```
File <module>-<version>.jar is already modular
```

Fix: `rm -rf <module>/target` before rebuilding that module (or just always
`clean` when touching it). This is a recurring, known, pre-existing quirk — not
a regression you introduced.

## Formatting

Code style is enforced by the **spring-javaformat-maven-plugin** (Spring's own
formatter — tabs, specific import ordering, no wildcard imports). It runs in
`validate` mode by default as part of the normal build, so a build with
unformatted code simply **fails**, it doesn't silently pass.

Before committing anything you touched, auto-fix formatting on just the modules
you changed (much faster than reformatting everything):

```bash
./mvnw -q -pl <module1>,<module2> spring-javaformat:apply
```

## Static analysis

A dedicated script (e.g. `bin/analyze.sh`) runs one or more Maven **profiles**
that each wire a different checker into the compiler via `-Xplugin:ErrorProne`
and friends:

- `errorprone` — general Error Prone bug patterns.
- `nullaway` — null-safety, using JSpecify-mode NullAway with the annotated
  package set to your root package.
- `checkerframework` — CheckerFramework's Nullness Checker (heavier, catches
  more, but has more friction/false-positive workarounds than NullAway).
- `eclipse` — compiles with the Eclipse batch compiler (`ecj`) instead of javac,
  catching lints/warnings javac doesn't.

Typical invocation (defaults to all profiles if none given):

```bash
./bin/analyze.sh "nullaway checkerframework errorprone"
```

Notes worth carrying over to a new project:
- These profiles are usually only wired up for **one "reference" module** (e.g.
  your core module) via `-pl core` inside the script, not the whole reactor.
  Extending coverage to every module is real, deliberate follow-up work, not an
  oversight to "fix" casually.
- If one of the profiles (commonly `eclipse`) has a **known pre-existing
  failure unrelated to your change**, don't burn time chasing it — confirm it
  fails identically on a clean checkout of the base branch, note it, and move
  on. Don't paper over it with `-DskipTests`-style shortcuts on the *other*
  profiles, though.
- Static analysis and normal `spring-javaformat` validation are **separate**
  concerns — a clean `clean install` does not mean the analyze profiles are
  clean, and vice versa. Run both before calling something done.

## Javadoc / documentation site

A dedicated script (e.g. `bin/doc.sh`) does a full aggregate javadoc build:

```bash
./mvnw --batch-mode --no-transfer-progress -Pdoc clean install -DskipTests=true
```

This is the **only** reliable way to verify cross-module `{@link}`/`{@value}`
javadoc references actually resolve — `mvn javadoc:javadoc` on a single module
won't catch a broken reference into another module, and a plain `clean install`
skips javadoc generation entirely by default.

Gotcha worth knowing up front: `{@value some.pkg.SomeClass#FIELD}` resolves a
**fully-qualified** reference into a non-exported package just fine (module
`exports` don't gate `{@value}`/`{@link}` resolution at all) — but an
**unqualified** simple-class-name reference from `module-info.java` specifically
(which has no package of its own to resolve relative to) does **not** resolve,
and fails with a generic "reference not found" error that looks like an
access/export problem but isn't one. If you hit this, fully-qualify the
reference before assuming you need to restructure packages or add exports.

## Git workflow

- **One git worktree per branch/task**, not a shared working directory you keep
  switching branches in:
  ```bash
  git worktree add ../<repo>-<short-task-name> -b <type>/<short-name> origin/main
  ```
  Always branch off `origin/main` (fetch first if it's been a while), not
  whatever the main worktree happens to be checked out to — that worktree may
  be mid-task on something unrelated.
- Branch name prefixes: `feature/`, `fix/`, `chore/`, `docs/`, `test/`,
  `refactor/` — pick the one matching the actual nature of the change.
- **Never push to `main` directly.** Push only to the feature branch; the repo
  owner merges themselves (via PR or otherwise) on their own schedule.
- If asked to rebase a branch after upstream `main` moved:
  ```bash
  git fetch origin
  git rebase origin/main
  ./mvnw -q clean install   # re-verify after rebase before force-pushing
  git push --force-with-lease origin <branch>
  ```
  `--force-with-lease`, never bare `--force` — it aborts instead of clobbering
  if someone else also pushed to that branch since you last fetched.
- Commit messages focus on **why**, not what (the diff already shows what).
  Only create commits when explicitly asked to.
- **Authorship trailer policy**: whether the agent adds itself as a co-author
  (`Co-Authored-By: <agent> <noreply@...>`) depends on whether it actually
  *authored* anything:
  - **Mechanical changes** — renames, moving code verbatim, applying a
    formatter, typing out exactly what was dictated in the prompt, a
    straightforward find/replace — do **not** get a co-author trailer. The
    agent was a typist here, not an author.
  - **Novel work** — the agent designed an approach, wrote logic/tests/docs
    that weren't dictated verbatim, or made non-trivial judgment calls about
    *how* to implement something — **does** get the co-author trailer,
    alongside the human author, not instead of them.
  - When in doubt, ask which bucket a given commit falls into rather than
    guessing either way.

  ```
  Co-Authored-By: <Agent Name> <noreply@anthropic.com>
  ```

## Testing conventions

- JUnit 5 (Jupiter). Prefer real objects and small in-memory test doubles you
  write yourself over mocking frameworks — this codebase leans toward
  hand-rolled fakes (e.g. an in-memory "capturing" implementation of an output
  interface) because they force you to actually match the real interface
  contract instead of stubbing around it.
- When a test needs to prove a specific low-level behavior (e.g. "this really
  used charset X, not just claimed to"), assert on **raw bytes**, not a
  string that's already been decoded somewhere on the way to the assertion —
  a decode step earlier in the pipeline can silently mask exactly the bug
  you're trying to catch.
- Golden-string assertions (exact expected multi-line output) are used freely
  and are considered a **feature**, not a smell, when testing formatters/
  encoders — they catch subtle regressions cheaply. Prefer them over "contains"
  assertions when the full expected output is short enough to inline.

## Before calling anything "done"

1. `./mvnw -q -pl <touched modules> spring-javaformat:apply`
2. `./mvnw -q -pl <touched modules> -am install` (module-scoped fast check)
3. `./bin/analyze.sh "..."` if you touched the analyzed reference module
4. `./mvnw -q clean install` (full reactor — catches cross-module breakage the
   scoped build can't see)
5. `./bin/doc.sh` if you touched any javadoc, `module-info.java`, or
   cross-module `{@link}`/`{@value}` references
6. Review the actual diff before committing — `git status`/`git diff` — never
   trust "the build passed" alone as proof the change is what you meant to
   make.
