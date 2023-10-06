package io.jstach.rainbowgum.rainbowgum;

import io.jstach.rainbowgum.Defaults;
import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.LockingLogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.benchmark.SLF4JBenchmark;

public class Main {

	public static void main(String[] args) {
		Defaults.logAppender = (config, output, format) -> new TestLogAppender(output, format);
		SLF4JBenchmark.main(args);
	}

	static class TestLogAppender extends AbstractLogAppender implements LockingLogAppender {

		public TestLogAppender(LogOutput output, LogFormatter formatter) {
			super(output, formatter);
		}

		@Override
		public void append(LogEvent event) {
			StringBuilder sb = new StringBuilder();
			formatter.format(sb, event);
			String message = sb.toString();
			synchronized (this) {
				output.write(event, message);
			}
			// output.flush();
		}

	}

}
