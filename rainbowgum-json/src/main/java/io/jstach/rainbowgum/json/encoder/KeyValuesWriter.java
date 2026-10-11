package io.jstach.rainbowgum.json.encoder;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.json.JsonBuffer;

/*
 * Writes key values as top-level fields, except a key value named like a field the
 * encoder writes itself. Like Json5Formatter, those go in a keyValues object, or as
 * keyValues. prefixed fields where the format is flat, so no field is written twice.
 * The consumers are created once per encoder so writing an event allocates nothing.
 */
final class KeyValuesWriter {

	static final String KEY_VALUES_FIELD = "keyValues";

	private static final String KEY_VALUES_PREFIX = KEY_VALUES_FIELD + ".";

	enum Placement {

		NESTED, DOTTED

	}

	private final Placement placement;

	private final Set<String> fields;

	private final int flag;

	private final KeyValuesConsumer<JsonBuffer> topLevel;

	private final KeyValuesConsumer<JsonBuffer> colliding;

	private final KeyValuesConsumer<@Nullable Void> counter;

	KeyValuesWriter(Placement placement, Set<String> fields, int flag) {
		this.placement = placement;
		this.fields = Set.copyOf(fields);
		this.flag = flag;
		this.topLevel = (kvs, k, v, index, buffer) -> collides(k) ? index : buffer.write(k, v, index, this.flag);
		this.colliding = switch (placement) {
			case NESTED -> (kvs, k, v, index, buffer) -> collides(k) ? buffer.write(k, v, index, 0) : index;
			case DOTTED -> (kvs, k, v, index, buffer) -> collides(k)
					? buffer.write(KEY_VALUES_PREFIX + k, v, index, this.flag) : index;
		};
		this.counter = (kvs, k, v, count, storage) -> v != null && collides(k) ? count + 1 : count;
	}

	private boolean collides(String key) {
		return fields.contains(key) || key.equals(KEY_VALUES_FIELD)
				|| (placement == Placement.DOTTED && key.startsWith(KEY_VALUES_PREFIX));
	}

	int write(KeyValues kvs, JsonBuffer buffer, int index) {
		index = kvs.forEach(topLevel, index, buffer);
		if (kvs.forEach(counter, 0, null) == 0) {
			return index;
		}
		return switch (placement) {
			case NESTED -> {
				int nested = buffer.writeObjectStart(KEY_VALUES_FIELD, index, flag);
				kvs.forEach(colliding, nested, buffer);
				buffer.writeObjectEnd();
				yield index + 1;
			}
			case DOTTED -> kvs.forEach(colliding, index, buffer);
		};
	}

}
