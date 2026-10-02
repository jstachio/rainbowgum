# rainbowgum-core

Nearly every other module depends on core, so core's test phase gates the rest of a
concurrent (`-T1C`) reactor build. Keep core's own tests fast.

Slow tests (real file I/O, threads, sleeps, timing, many iterations) go in
[`test/rainbowgum-test-core`](../test/rainbowgum-test-core) instead. It depends on core
like any consumer and uses the same `io.jstach.rainbowgum` package, so it can test
package-private API too. See also [development.md](../development.md).
