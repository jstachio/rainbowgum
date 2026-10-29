package io.jstach.rainbowgum.format;

import java.util.List;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * Which thread fields the core <code>json5</code> and <code>logfmt</code> encoders write.
 */
@CaseChanging
public enum ThreadFields {

	/**
	 * Thread name as <code>thread</code>.
	 */
	@EnumAlias("true")
	NAME,
	/**
	 * Thread id as <code>threadId</code>.
	 */
	ID,
	/**
	 * Thread id and name as <code>threadId</code> and <code>threadName</code>. The
	 * default, since a virtual thread's name is usually empty.
	 */
	@EnumAlias("default")
	BOTH,
	/**
	 * No thread.
	 */
	@EnumAlias("false")
	NONE;

	/*
	 * The field names written, which key values must not repeat.
	 */
	List<String> fields() {
		return switch (this) {
			case NAME -> List.of("thread");
			case ID -> List.of("threadId");
			case BOTH -> List.of("threadId", "threadName");
			case NONE -> List.of();
		};
	}

	static ThreadFields parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "default" -> BOTH;
			case "true" -> NAME;
			case "false" -> NONE;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(ThreadFields.class, value, "true", "false", "default");
		};
	}

}
