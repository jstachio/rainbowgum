# Agent build issues

Agents should report build difficulties here, including cache problems, flaky
tests, stale artifacts, and unnecessary waits. The goal is to keep the time
agents spend waiting for build tools as short as possible while preserving
reliable results. Read [build.md](../build.md) before choosing build commands.

For each entry, record the command, observed symptoms, why a workaround was
needed, and what remains uncertain. Distinguish observations from suspected
causes. Explain how to return to a faster build rather than turning a temporary
diagnostic workaround into a requirement for every change. Identify yourself
at the end of each entry (agent name and model, if known).

## 2026-09-30: Golden string failures and stale runtime behavior

While fixing the startup diagnostics tests, module tests and builds involving
cached artifacts produced inconsistent output that appeared to come from older
code. An uncached full `clean verify` produced consistent results across the
reactor. This did not establish that the build cache itself was defective:
stale packaged dependency jars and stopping at the `test` phase were also
possible contributors. A downstream module can resolve a packaged dependency
jar that has not been refreshed by a test-only build.

The diagnostic build used:

```sh
./mvnw -T1 --fail-at-end \
  -Dmaven.build.cache.enabled=false \
  -Dmaven.test.failure.ignore=true clean verify
```

The choices served different purposes:

* `-Dmaven.build.cache.enabled=false` removed cache restoration as a variable.
  The cache is normally off by default, but can be enabled by `-Pcache` or
  `RAINBOWGUM_CACHE=true`. The explicit override made this run independent of
  those settings.
* `clean verify` rebuilt and packaged dependencies before their consumers ran,
  avoiding reliance on old jars while checking the whole reactor.
* `-T1` replaced the repository's default `-T2C` with a serial reactor build to
  simplify diagnosis and the ordering of output. No parallel-build defect was
  demonstrated. This was a diagnostic precaution, not a fix for golden string
  assertions or a reason to disable parallel builds routinely.
* `--fail-at-end` let independent modules continue after a failure. Maven can
  still skip modules that depend on a failed module.
* `-Dmaven.test.failure.ignore=true` let modules with assertion failures finish
  so downstream tests could run too. This collected golden string failures
  across the build in one pass. A successful Maven exit with this option does
  not prove the tests passed; all Surefire and Failsafe XML reports were checked.

Final verification used the same command with
`-Dmaven.test.failure.ignore=false`. The most recent full run completed in about
55 seconds with 20,107 tests and no failures or errors.

For routine changes, prefer the smallest build that verifies the behavior and
retain parallelism or caching when results are reliable. When changing a module
used by other modules, make sure its packaged artifact is current. If stale
behavior recurs, capture the cache settings, resolved dependency artifacts, and
cached versus uncached results before concluding that caching or parallelism is
the cause. The cache's role in the earlier inconsistent output remains
unconfirmed.

Agent: Codex (GPT-6).
