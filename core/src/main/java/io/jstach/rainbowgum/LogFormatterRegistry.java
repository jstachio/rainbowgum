package io.jstach.rainbowgum;

import java.util.EnumMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.StandardEventFormatter;

/**
 * Formatters that are registered based on output type.
 */
public sealed interface LogFormatterRegistry permits DefaultLogFormatterRegistry {

	/**
	 * Creates a log formatter registry.
	 * @return new created log formatter registry.
	 */
	public static LogFormatterRegistry of() {
		return new DefaultLogFormatterRegistry();
	}

	/**
	 * Associates a default formatter with a specific output type
	 * @param outputType output type to use for finding best default formatter.
	 * @return formatter for output type.
	 */
	public LogFormatter formatterForOutputType(OutputType outputType);

	/**
	 * Sets a default formatter for a specific output type.
	 * @param outputType output type.
	 * @param formatter formatter.
	 */
	public void setFormatterForOutputType(OutputType outputType, Supplier<? extends LogFormatter> formatter);

}

final class DefaultLogFormatterRegistry implements LogFormatterRegistry {

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final EnumMap<OutputType, Supplier<? extends LogFormatter>> formatters = new EnumMap<>(OutputType.class);

	/**
	 * Associates a default formatter with a specific output type
	 * @param outputType output type to use for finding best default formatter.
	 * @return formatter for output type.
	 */
	public LogFormatter formatterForOutputType(OutputType outputType) {
		lock.readLock().lock();
		try {
			var formatter = formatters.get(outputType);
			if (formatter == null) {
				return StandardEventFormatter.builder().build();
			}
			return formatter.get();
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Sets a default formatter for a specific output type.
	 * @param outputType output type.
	 * @param formatter formatter.
	 */
	public void setFormatterForOutputType(OutputType outputType, Supplier<? extends LogFormatter> formatter) {
		lock.writeLock().lock();
		try {
			formatters.put(outputType, formatter);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

}
