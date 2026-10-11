# Rainbow Gum consumer QA: GPT-6.1 Sol, 20592241

Agent: Codex subagent `/root/blackbox_qa` (GPT-6.1 Sol, high reasoning).

Test date: 2026-10-11. Tested Git commit: `20592241eac43ba0c652fb621b13fdda1aefab18`.

This report covers the pinned commit, not subsequent changes. Reproduction commands refer to the retained local workspace `/home/agentcodex/qa/rainbowgum-20592241`; consumer projects, transcripts, and support files are retained there and are not included in this documentation commit.

The documented runtime dependency works, and the tested logging workflows behaved as documented after correcting two QA assumptions. The first pass verified four documentation problems, including two independent blockers in the Getting Started shell example and an incomplete builder example. No implementation defect was confirmed.

The corrected automated consumer suite passed **36 process runs and 115 assertions**. Additional first-attempt builds, Getting Started failures/corrections, no-config runs, and one focused JUL timing follow-up are retained separately. These are real consumer JVM executions; no Rainbow Gum project tests were used. Maven builds use `-DskipTests` because assertions are run by the independent Python process harness, not Surefire.

## Scope and environment

- Rainbow Gum commit supplied: `20592241eac43ba0c652fb621b13fdda1aefab18`.
- Artifact version: `1.0.0-SNAPSHOT` throughout.
- OpenJDK: Temurin 27+35, Linux; consumer compilation uses `--release 21`. This does **not** verify runtime compatibility on JDK 21.
- Supplied Maven wrapper: Maven 3.10.0. Every build invoked this workspace's `./mvnw` with `-Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository`.
- SLF4J API: explicit compile dependency `2.0.20`. The guide's runtime binding dependency does not replace an application's compile dependency on the facade.
- Rainbow Gum information came exclusively from `doc/overview.html`, rendered `api/index.html`, and selected public API pages. Only compiled runtime artifacts under `repository/` were consumed.
- No Rainbow Gum source, source JAR, src-html page, implementation inspection/decompilation, project tests, Git metadata/history, issues/PRs, other workspace, or memories were read. API pages contain source links; those links were not followed.
- An initial workspace **file-name listing** unexpectedly exposed a supplied `build-cache/` directory containing names of excluded artifacts. No file in that cache was opened. It was immediately reported and the parent removed it. This procedural-isolation limitation is disclosed rather than claiming operating-system isolation.
- The plan was written before the parent declared artifacts/API docs ready. No Maven command ran before readiness.

Supporting files in the retained local workspace: `QA-PLAN.md`, `FIRST-ATTEMPT-NOTES.md`, and `RERUN.md`. Guide links below refer to the tested commit. External API links point to the published documentation; the agent used locally generated API documentation from the tested commit.

## Verified findings

### D1: Getting Started shell example omits `-jar`

**Classification:** documentation. **Impact:** the first copied launch command cannot start the application.

**Documentation:** [Using System properties](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L50), also raw `doc/overview.html` lines 50–63. The rendered example ends with `myapp.jar` after JVM options and says Rainbow Gum will load and configure from those properties.

**Expected:** the example starts an executable application jar.

**Actual:** substituting only the produced consumer jar path, the command exits **1** before the application runs:

```text
Error: Could not find or load main class consumers.standard.target.standard-1.0.jar
Caused by: java.lang.ClassNotFoundException: consumers.standard.target.standard-1.0.jar
```

**Minimal reproduction after building `standard`:**

```bash
java \
  -Dlogging.appender.console.encoder.uri=pattern \
  '-Dlogging.appender.console.encoder.pattern.pattern=[%thread] %-5level %logger{15} - %msg%n' \
  -Dlogging.level.com.myapp=DEBUG \
  consumers/standard/target/standard-1.0.jar
```

**Correction checked:** adding `-jar` gets past JVM launch and exposes D2. Supplying both `-jar` and the optional pattern module starts successfully.

**Evidence:** `transcripts/02-literal-guide-launch/` preserves the exact shell command, stdout, stderr, and exit. Follow-ups: `02-corrected-jar-standard/`, `02-corrected-pattern-dependency/`.

### D2: Getting Started pattern example does not mention its required optional module

**Classification:** documentation. **Impact:** standard documented installation still fails after repairing D1.

**Documentation:** [Using System properties](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L50) only assumes the [installation dependency](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L2002), whose runtime artifact is `rainbowgum`. The later [Pattern Formatter](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L1153) section explicitly says the pattern module is not in the aggregate dependency.

**Expected:** the Getting Started configuration is usable with its stated installation prerequisites, or names the extra prerequisite.

**Actual:** with the documented standard runtime aggregate, adding `-jar` to the command exits **1** during logger initialization:

```text
No encoder found. Scheme not registered. scheme: 'pattern', URI: 'pattern:///'.
Scheme 'pattern' is provided by module 'io.jstach.rainbowgum.pattern'
(Maven: 'io.jstach.rainbowgum:rainbowgum-pattern:1.0.0-SNAPSHOT'). Add that dependency.
```

The product's error is helpful and correctly names the dependency; the missing prerequisite is in the guide.

**Minimal reproduction:** run D1's command with `-jar consumers/standard/target/standard-1.0.jar` as the final arguments.

**Correction checked:** `consumers/extras/pom.xml` adds the documented `rainbowgum-pattern` dependency. The same options with `-jar consumers/extras/target/extras-1.0.jar` exit **0**, include DEBUG for `com.myapp` descendants, and suppress DEBUG for sibling/case-different names. `extras` also contains file/JFR modules for later cases; those modules are not needed for this correction.

**Evidence:** `transcripts/02-corrected-jar-standard/` and `02-corrected-pattern-dependency/`.

### D3: Getting Started builder snippet leaves the appender output unset

**Classification:** documentation. **Impact:** copied and correctly registered provider compiles but cannot initialize logging.

**Documentation:** expanded [Using RainbowGum Builders](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L66), plus [public SPI registration instructions](https://jstach.io/rainbowgum/io.jstach.rainbowgum/io/jstach/rainbowgum/spi/RainbowGumServiceProvider.html#class-description). The copied snippet sets a DEBUG prefix and a pattern encoder on its `console` appender, but no output.

**Expected:** the advertised simple programmatic example starts after adding the documented compile dependencies, imports, package, and base-interface service registration.

**Actual:** the provider is discovered, its code is invoked, and startup exits **1**:

```text
Property missing. key:
    'logging.appender.console.output.uri' from:
        SYSTEM_PROPERTIES[logging.appender.console.output.uri]
```

**Minimal reproduction after building `builder`:**

```bash
java -jar consumers/builder/target/builder-1.0.jar
```

The exact copied provider and `META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider` file are retained in `consumers/builder/` and the first-attempt source snapshot.

**Correction checked:**

```bash
java -Dlogging.appender.console.output.uri=stdout \
  -jar consumers/builder/target/builder-1.0.jar
```

This exits **0** without changing the provider source. A further run verifies a configured pattern overrides the snippet's code default via `fromProperties`.

**Evidence:** `transcripts/03-builder-build/`, `03-builder-run/`, `03-builder-property-correction/`, and `03-builder-override/`.

### D4: Overview overstates queuing/replay coverage for JUL before initialization

**Classification:** documentation. **Impact:** a consumer may expect early JUL records to use Rainbow Gum's destinations and formatting when they still use native JUL handlers.

**Documentation:** [java.lang.System.Logger and java.util.logging](https://github.com/jstachio/rainbowgum/blob/20592241eac43ba0c652fb621b13fdda1aefab18/doc/overview.html#L2246) says the integration avoids early initialization by queuing events from both facilities when another facade is detected and replays them when Rainbow Gum binds. The [JUL module API](https://jstach.io/rainbowgum/io.jstach.rainbowgum.jul/module-summary.html#module-description) explains the narrower behavior: its bridge handler is installed only during Rainbow Gum's bootstrap and plain JUL has no service-loader initialization hook.

**Expected from the overview:** a JUL INFO record emitted before SLF4J initializes is queued and later delivered through Rainbow Gum, like an early System.Logger record.

**Actual:** the default aggregate is present. The consumer emits a System.Logger record, a JUL record, then an SLF4J record that initializes Rainbow Gum. System.Logger replays through Rainbow Gum stdout; early JUL uses JUL's native stderr handler and is not replayed. JUL after initialization uses Rainbow Gum stdout:

```text
stdout:
... INFO  com.myapp.System - BEFORE_SYSTEM
... INFO  com.myapp.Slf - SLF_INIT
... INFO  com.myapp.Jul - AFTER_JUL
... INFO  com.myapp.System - AFTER_SYSTEM

stderr:
Oct 11, 2026 3:42:54 PM com.myapp.App main
INFO: BEFORE_JUL
```

**Minimal reproduction after building `lifecycle`:**

```bash
java -jar consumers/lifecycle/target/lifecycle-1.0.jar jul
```

**Assessment:** this is not classified as an implementation defect because the public JUL module documentation explains the observed limitation. Qualify the overview's early-event claim and make the before-bootstrap JUL boundary clear.

**Evidence:** first attempt `transcripts/14-jdk-replay/`; focused confirmation `transcripts/14-jul-timing-followup/`. The corrected suite explicitly checks both output channels.

### E1: Normal shell sandbox fails before executing commands

**Classification:** environment. **Impact:** ordinary shell/tool invocations cannot start; permitted escalation was necessary.

Initial `pwd`, file listing, and overview-reading commands each exited **1** with:

```text
bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted
```

These failed before Rainbow Gum or Maven executed. Escalated shell reads, builds, and runs worked. The supplied Maven configuration also emits a nonfatal warning that requested profile `cache` does not exist; all five consumer builds succeeded.

**Evidence:** `transcripts/00-environment-first-attempt.txt` and the five build transcripts. This is not a Rainbow Gum product finding.

## Successful coverage

| Scenario group | Verified behavior |
|---|---|
| Standard Maven installation/defaults | Runtime aggregate binding; no-config INFO threshold; default TTLL output; DEBUG suppressed |
| Getting Started system properties | Corrected executable-jar launch; optional pattern dependency; prefix DEBUG and logger abbreviation |
| Service-loader builder | Base SPI registration/discovery; corrected output; properties override code pattern |
| Simple bundle without base resource | Starts without config; missing selected profile does not fail without base; environment properties not contributed |
| Simple resource/profile loading | Prefix-free keys; default profile; first selected profile wins; system > environment > profile > base; environment profile selector; required missing profile failure; empty system selector replaces environment selector and uses optional default profile |
| Level resolution/groups | Dotted boundaries; case-sensitive names; case-insensitive JUL aliases; child override; explicitly enabled groups cover descendants |
| Routes | Route WARN default defeats deeper global TRACE; file DEBUG namespace isolated with OFF default |
| TTLL/context | Fluent key values override MDC; Unicode/separators/newlines percent encoded; null vs empty retained; plain property wins over URI query; JSON empty object; invalid JSON+omit fails |
| Core JSON | Strict JSON parses; reserved `msg` context kept nested; null/empty/fluent values preserved; JSON ignores pretty/color settings |
| Logfmt/pattern throwable | Logfmt stays one line with escaped stacktrace; automatic pattern throwable; `%nopex` suppression; `%rEx` root first with `Wrapped by:` |
| Console streams | Creation-time stdout capture; `stream=follow` respects later `System.setOut` |
| File output | Bare relative `logging.file.name`; generic `./` URI; explicit append/truncate; no default ANSI |
| Size-based rolling | Small byte threshold rotates; two retained archives plus active file; final event delivered and old history removed |
| Async/JDK integration | Batch-swap buffer of 8 delivers 1,000 ordered events exactly once on ordinary shutdown; early System.Logger replay; JUL before/after startup distinction |
| Diagnostics | `help` typo suggestion/failure; `all` does not fail; unrelated unread key informational; strict unread-key failure; alert failure configured in resource |
| Supplemental JFR | URI-started recording persisted with no JFC/JVM flags; `jfr print` shows DEBUG and INFO records |

The core verified suite contains 36 runs / 115 assertions; data is in `qa-results-verified.json`. The first suite also performed 36 runs / 115 assertions and had two failed QA expectations, retained in `qa-results.json` and `first-attempts/qa-results.json`.

## First-attempt interpretation corrections

An explicitly empty profile list was initially assumed to expose only the base resource. That assertion was incorrect: the public `SimpleProperties` API states that an empty selection uses the optional default profile, and the overview says the default profile applies when no profiles are selected. The revised check verifies empty system selection overrides a nonempty environment selection and still loads `logging-default.properties`. **No profile defect is claimed.**

The early-JUL assertion initially expected Rainbow Gum stdout based on the overview. Inspecting the module API and running a focused before/after consumer established D4's documentation mismatch. The corrected suite checks the documented module behavior, retaining the original failing expectation.

No original first-attempt source, command, stdout/stderr, exit status, or result file was overwritten by these corrections.

## Reproducibility and limitations

Follow `RERUN.md` in the retained local workspace to rebuild the five consumers with the isolated wrapper/repository and execute the suite. Property-resource fixtures are actual files in `cases/` placed first on the JVM classpath; they are not packaged into the simple consumer jar. That tests classpath-resource loading but not Maven's resource-copying step for these fixtures. Runtime aggregate, simple no-config, and builder service registration were tested as executable Maven-produced jars.

This is a focused HotSpot/classpath pass, not a certification. Not tested: Spring Boot/Helidon/Micronaut installations, remote OTLP collectors, production JSON schema modules, default/scoped key-value providers, annotation processor extensions, GraalVM/native-image, JPMS/jlink, real ANSI terminals, performance claims, custom filters/decorators, file reopen/logrotate, prudent locking, multiple processes, async overload/interruption/timeout/failure, large concurrency, watched level-file reload, or runtime-level changes. A lifecycle consumer contains a prepared dynamic-level code path that was **not executed**; only its `jul` mode was used to settle the existing finding. The optional `rainbowgum-jdk`-only eager-start setup was not separately built.

Rolling coverage confirms bounded retention and the final event, not preservation of all historical events intentionally deleted by the retention policy. Async coverage confirms one bounded producer/process shutdown path, not all races. JFR coverage confirms persisted application events, not alert events or externally configured sessions. Successful diagnostics assertions verify useful key names and suggestions, not a complete report schema.

Report author: Codex subagent `/root/blackbox_qa`, GPT-6.1 Sol (high reasoning). Tested commit: `20592241eac43ba0c652fb621b13fdda1aefab18`.
