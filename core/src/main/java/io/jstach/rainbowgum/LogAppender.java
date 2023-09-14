package io.jstach.rainbowgum;

public interface LogAppender {

	public void append(
			LogEvent event);
	
	public static LogAppender of(LogOutput output, LogFormatter formatter) {
		return new DefaultLogAppender(output, formatter);
	}
}

record DefaultLogAppender(LogOutput output, LogFormatter formatter) implements LogAppender {
	@Override
	public void append(
			LogEvent event) {
		formatter.format(output, event);
		
	}
}
