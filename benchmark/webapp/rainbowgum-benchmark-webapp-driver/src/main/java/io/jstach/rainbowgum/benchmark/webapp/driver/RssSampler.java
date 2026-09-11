package io.jstach.rainbowgum.benchmark.webapp.driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Polls {@code /proc/<pid>/status} for {@code VmRSS} on a background thread while a load
 * run is in progress.
 */
final class RssSampler {

	private final Thread thread;

	private final AtomicBoolean running = new AtomicBoolean(true);

	private final List<Long> samplesKb = new CopyOnWriteArrayList<>();

	private RssSampler(long pid) {
		Path statusPath = Path.of("/proc", Long.toString(pid), "status");
		this.thread = Thread.ofPlatform().daemon().name("rss-sampler").start(() -> {
			while (running.get()) {
				Long kb = readVmRssKb(statusPath);
				if (kb != null) {
					samplesKb.add(kb);
				}
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
	}

	static RssSampler start(long pid) {
		return new RssSampler(pid);
	}

	Stats stop() {
		running.set(false);
		try {
			thread.join(2000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (samplesKb.isEmpty()) {
			return new Stats(0, 0, 0);
		}
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		long sum = 0;
		for (long v : samplesKb) {
			min = Math.min(min, v);
			max = Math.max(max, v);
			sum += v;
		}
		double avg = sum / (double) samplesKb.size();
		return new Stats(min / 1024d, max / 1024d, avg / 1024d);
	}

	private static @Nullable Long readVmRssKb(Path statusPath) {
		try {
			for (String line : Files.readAllLines(statusPath)) {
				if (line.startsWith("VmRSS:")) {
					String digits = line.replaceAll("[^0-9]", "");
					if (!digits.isEmpty()) {
						return Long.parseLong(digits);
					}
				}
			}
		}
		catch (IOException e) {
			/*
			 * The target process may have exited between our poll interval and this read;
			 * just stop contributing samples rather than failing the whole run.
			 */
		}
		return null;
	}

	record Stats(double minMb, double maxMb, double avgMb) {
	}

}
