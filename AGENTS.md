# Repository instructions

Before editing, read [development.md](development.md) and [build.md](build.md).
They contain the project's development conventions and build procedures.

## Tests and diagnostic strings

- Compare complete error messages, log output and other captured output
  (alerts, `MetaLog`, stderr) with `assertEquals` and Java text blocks (`"""`).
  Normalize only nondeterministic values to stable placeholders. Do not use
  `contains`, `startsWith` or other partial assertions.
- Check that a new regression test fails without the fix, and that the failing
  run reports the test. See the Testing section of `development.md`.
- Prefer end to end tests and parameterized enum driven cases. Slow tests belong
  in the appropriate module under `test/`. Do not add tests merely for coverage.

## Required validation

- For Java or Java build changes, run the applicable tests and Spring Java Format
  validation through `./mvnw`, then run `bin/analyze.sh` before pushing.
  Passing tests alone does not complete validation.
- The analysis script defines the supported analyzers and module exclusions.
  Use its documented defaults or options that cover the affected modules. Do not
  replace it with an improvised selection of Maven profile commands or silently
  omit supported analyzers.
- For documentation-only changes, check the final diff, formatting, and local
  links. Java tests and analyzers are not required for prose-only edits.
- If a required check cannot finish, report the exact blocker and which checks
  remain unverified. Do not claim a check passed without a successful run.
  Follow the build-issue reporting instructions in `development.md`.

## Before finishing

Review the final diff against these instructions and `development.md`. Report
the validation commands actually completed, their scope, and any remaining gaps.
Keep `main` linear, use rebase instead of merge, and do not squash agent commits
unless the user requests it.
