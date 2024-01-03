package io.jstach.rainbowgum.apt;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;

public class ExampleBuilder {

	private Integer count;

	public static final String PROPERTY_PREFIX = "logging.rabbitmq.";

	public static final String PROPERTY_COUNT = PROPERTY_PREFIX + "count";

	private static final Property<Integer> countProperty = Property.builder().toInt().build(PROPERTY_COUNT);

	public ExampleBuilder count(Integer count) {
		this.count = count;
		return this;
	}

	public Example build() {
		return new Example(count);
	}

	public ExampleBuilder fromProperties(LogProperties properties) {
		this.count = countProperty.get(properties).value(this.count);
		return this;
	}

}
