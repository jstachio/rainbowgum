package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

@Timeout(30)
class PackagingDataCacheTest {

	private static final String PREFIX = "missing.packagingcache.";

	private static final int THREADS = 32;

	@Test
	void fullCacheKeepsHitsAndStillResolvesNewFrames() {
		var original = Thread.currentThread().getContextClassLoader();
		var loader = new MissingClassLoader(original);
		Thread.currentThread().setContextClassLoader(loader);
		try {
			var formatter = ThrowableFormatter.builder().packagingData(true).build();
			fill(formatter, PackagingDataResolver.MAX_CACHED);
			assertEquals(PackagingDataResolver.MAX_CACHED, loader.attempts.get());
			var cached = frame(PREFIX + "Script0");
			var uncached = frame(PREFIX + "Uncached");
			assertEquals("""
					java.lang.IllegalStateException: cache test
						at missing.packagingcache.Script0.run(Script.java:1) [na:na]
						at missing.packagingcache.Uncached.run(Script.java:1) [na:na]
					""", render(formatter, cached, uncached));
			assertEquals(PackagingDataResolver.MAX_CACHED + 1, loader.attempts.get());
			render(formatter, cached, uncached);
			assertEquals(PackagingDataResolver.MAX_CACHED + 2, loader.attempts.get());
			var packaged = new StackTraceElement("test-loader", "example.module", "1.2", PREFIX + "Packaged", "run",
					"Script.java", 1);
			assertEquals(
					"""
							java.lang.IllegalStateException: cache test
								at test-loader/example.module@1.2/missing.packagingcache.Packaged.run(Script.java:1) [example.module:1.2]
							""",
					render(formatter, packaged));
			assertEquals(PackagingDataResolver.MAX_CACHED + 2, loader.attempts.get());
		}
		finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	@Test
	void concurrentMissesShareTheLastCacheSlot() throws Exception {
		var resolver = new PackagingDataResolver();
		String lockKey = collidingClassName(0);
		resolver.resolve(moduleFrame(lockKey));
		for (int i = 1; i < PackagingDataResolver.MAX_CACHED - 1; i++) {
			resolver.resolve(moduleFrame(PREFIX + "Script" + i));
		}
		var field = PackagingDataResolver.class.getDeclaredField("cache");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		var cache = (ConcurrentMap<String, String>) field.get(resolver);
		List<Thread> threads = new ArrayList<>();
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		/*
		 * Hold a cache bucket while new, colliding keys are resolved. In the draft, every
		 * writer can pass size() before blocking in put(). This makes the capacity race
		 * deterministic without changing the resolver or cache contents.
		 */
		cache.compute(lockKey, (key, value) -> {
			for (int i = 1; i <= THREADS; i++) {
				var candidate = moduleFrame(collidingClassName(i));
				threads.add(Thread.ofPlatform().daemon().start(() -> {
					try {
						resolver.resolve(candidate);
					}
					catch (Throwable failure) {
						failures.add(failure);
					}
				}));
			}
			long start = System.nanoTime();
			while (threads.stream().anyMatch(t -> t.getState() != Thread.State.BLOCKED)) {
				assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10),
						"cache writers did not reach the blocked admission point");
				LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
			}
			return value;
		});
		for (var thread : threads) {
			assertTrue(thread.join(Duration.ofSeconds(10)), "cache writer did not finish");
		}
		assertEquals(List.of(), failures);
		assertEquals(PackagingDataResolver.MAX_CACHED, cache.size());
	}

	private static StackTraceElement moduleFrame(String className) {
		return new StackTraceElement("test-loader", "example.module", "1.2", className, "run", "Script.java", 1);
	}

	// Six pairs of "Aa" and "BB" give 64 distinct names with the same String hash.
	private static String collidingClassName(int index) {
		var name = new StringBuilder(PREFIX + "Concurrent");
		for (int bit = 0; bit < 6; bit++) {
			name.append((index & (1 << bit)) == 0 ? "Aa" : "BB");
		}
		return name.toString();
	}

	private static StackTraceElement frame(String className) {
		return new StackTraceElement(className, "run", "Script.java", 1);
	}

	private static void fill(ThrowableFormatter formatter, int count) {
		var frames = new StackTraceElement[count];
		for (int i = 0; i < count; i++) {
			frames[i] = frame(PREFIX + "Script" + i);
		}
		render(formatter, frames);
	}

	private static String render(ThrowableFormatter formatter, StackTraceElement... frames) {
		var throwable = new IllegalStateException("cache test");
		throwable.setStackTrace(frames);
		var output = new StringBuilder();
		formatter.formatThrowable(output, throwable);
		return output.toString().replace(LogFormatter.lineSeparator(), "\n");
	}

	private static final class MissingClassLoader extends ClassLoader {

		final AtomicInteger attempts = new AtomicInteger();

		MissingClassLoader(@Nullable ClassLoader parent) {
			super(parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.startsWith(PREFIX)) {
				return super.loadClass(name, resolve);
			}
			attempts.incrementAndGet();
			throw new ClassNotFoundException(name);
		}

	}

}
