import java.util.Arrays;

/**
 * Minimal immutable key-value pairs, index-based rather than java.util.Map (deliberately
 * mirroring the shape Rainbow Gum's own core/src/main/java/io/jstach/rainbowgum/KeyValues.java
 * already uses) - kept as a tiny standalone interface here rather than importing that
 * type directly, since this whole prototype is meant to stay implementation-neutral.
 */
public interface KeyValues {

	int size();

	String key(int index);

	String value(int index);

	static KeyValues of() {
		return EMPTY;
	}

	static KeyValues of(String[] keys, String[] values) {
		if (keys.length != values.length) {
			throw new IllegalArgumentException("keys.length != values.length");
		}
		if (keys.length == 0) {
			return EMPTY;
		}
		return new ArrayKeyValues(keys.clone(), values.clone());
	}

	/**
	 * Merges several KeyValues into one, in the given order - later entries with a
	 * duplicate key still appear (no de-duping, kept simple for the prototype).
	 */
	static KeyValues merge(java.util.List<KeyValues> parts) {
		int total = 0;
		for (var p : parts) {
			total += p.size();
		}
		if (total == 0) {
			return EMPTY;
		}
		String[] keys = new String[total];
		String[] values = new String[total];
		int i = 0;
		for (var p : parts) {
			for (int j = 0; j < p.size(); j++) {
				keys[i] = p.key(j);
				values[i] = p.value(j);
				i++;
			}
		}
		return new ArrayKeyValues(keys, values);
	}

	KeyValues EMPTY = new ArrayKeyValues(new String[0], new String[0]);

}

record ArrayKeyValues(String[] keys, String[] values) implements KeyValues {

	@Override
	public int size() {
		return keys.length;
	}

	@Override
	public String key(int index) {
		return keys[index];
	}

	@Override
	public String value(int index) {
		return values[index];
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("{");
		for (int i = 0; i < keys.length; i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(keys[i]).append("=").append(values[i]);
		}
		return sb.append("}").toString();
	}

}
