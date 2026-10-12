# Rainbow Gum consumer QA rerun: GPT-6.1 Sol, 20592241

Agent: Codex subagent `/root/blackbox_qa` (GPT-6.1 Sol, high reasoning). Tested commit: `20592241eac43ba0c652fb621b13fdda1aefab18`.

See the [QA report](qa-report-gpt-6.1-sol-20592241.md) for findings and coverage limits. These commands require the retained QA workspace, including its consumer projects, fixtures, scripts, and supplied artifacts. Those files are not included in this repository; this guide does not bootstrap a new workspace.

Work in `/home/agentcodex/qa/rainbowgum-20592241`. Use the supplied Java toolchain and wrapper; keep Maven downloads in this workspace's repository. Do not inspect excluded source/test/history artifacts. On this machine shell commands need the approved sandbox escalation because the normal sandbox fails before execution.

Build each consumer from the workspace root:

```bash
./mvnw -Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository -f consumers/standard/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests
./mvnw -Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository -f consumers/simple/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests
./mvnw -Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository -f consumers/extras/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests
./mvnw -Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository -f consumers/builder/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests
./mvnw -Dmaven.repo.local=/home/agentcodex/qa/rainbowgum-20592241/repository -f consumers/lifecycle/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests
```

These commands package consumer jars and copy runtime dependencies into each `target/lib`. Surefire is skipped; real assertions are in the independent process suite:

```bash
python3 qa-suite.py
```

Expected: `36 runs, 115 checks, 0 runs with failed expectations`, exit **0**. The suite rewrites its own generated case-output files and corrected-suite transcripts under `transcripts/verified-suite`; original first-attempt evidence at `transcripts/01-*` through `17-*` and `first-attempts/` remains separate. Resource fixtures under `cases/profiles` and `cases/ci-alerts` are put directly on the JVM classpath before consumer classes.

The suite's case commands are recorded as argv arrays, working directory, environment overrides, and exit status in `command.json`; separate `stdout.txt` and `stderr.txt` preserve both channels. Current aggregate assertions are in `qa-results-verified.json`; original assertions/results are in `first-attempts/` and `qa-results.json`. No secrets or full inherited environment were dumped.

Reproduce specific guide problems:

```bash
# D1: missing -jar; exits 1 before application initialization.
java -Dlogging.appender.console.encoder.uri=pattern '-Dlogging.appender.console.encoder.pattern.pattern=[%thread] %-5level %logger{15} - %msg%n' -Dlogging.level.com.myapp=DEBUG consumers/standard/target/standard-1.0.jar

# D2: fixed jar launch, standard aggregate lacks optional pattern module; exits 1.
java -Dlogging.appender.console.encoder.uri=pattern '-Dlogging.appender.console.encoder.pattern.pattern=[%thread] %-5level %logger{15} - %msg%n' -Dlogging.level.com.myapp=DEBUG -jar consumers/standard/target/standard-1.0.jar

# Both corrections: exits 0.
java -Dlogging.appender.console.encoder.uri=pattern '-Dlogging.appender.console.encoder.pattern.pattern=[%thread] %-5level %logger{15} - %msg%n' -Dlogging.level.com.myapp=DEBUG -jar consumers/extras/target/extras-1.0.jar

# D3: copied builder provider without output; exits 1.
java -jar consumers/builder/target/builder-1.0.jar

# D3 correction without changing Java source; exits 0.
java -Dlogging.appender.console.output.uri=stdout -jar consumers/builder/target/builder-1.0.jar

# D4: early JUL stderr vs post-start JUL stdout; exits 0.
java -jar consumers/lifecycle/target/lifecycle-1.0.jar jul
```

`run-command.py` can capture a fresh reproduction under a **new** transcript name. Avoid reusing a first-attempt name:

```bash
python3 run-command.py my-new-reproduction java -jar consumers/lifecycle/target/lifecycle-1.0.jar jul
```

The builder's original code remains deliberately incomplete so D3 is reproducible. Its corrected suite invocation supplies stdout as a property. The lifecycle dynamic-level branch is prepared but untested and is not part of the QA claims.
