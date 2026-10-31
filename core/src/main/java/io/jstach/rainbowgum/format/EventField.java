package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * A field every event has, for the <code>columns</code> property of the <code>ltsv</code>
 * encoder.
 */
@CaseChanging
public enum EventField {

	/**
	 * Time.
	 */
	@EnumAlias("timestamp")
	TIME,
	/**
	 * Level.
	 */
	LEVEL,
	/**
	 * Logger name.
	 */
	LOGGER,
	/**
	 * Thread fields, see {@link ThreadFields}.
	 */
	THREAD,
	/**
	 * Message.
	 */
	@EnumAlias("msg")
	MESSAGE;

	static EventField parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "timestamp" -> TIME;
			case "msg" -> MESSAGE;
			default -> io.jstach.rainbowgum.LogProperty.enumValue(EventField.class, value, "timestamp", "msg");
		};
	}

}
