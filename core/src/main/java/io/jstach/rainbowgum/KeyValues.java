package io.jstach.rainbowgum;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * Key Value Pairs similar to a {@code Map<String,String>} but optimized for memory and
 * less garbage (no iterators).
 * <p>
 * Key values iterate in a stable order: key values built the same way from the same input
 * always iterate the same way, so formatted output does not reorder from one event to the
 * next. The current implementations use insertion order, and
 * {@link #merge(KeyValues, KeyValues)} keeps an overridden key at its original position,
 * but insertion order itself is not part of this contract.
 * <p>
 * The preferred way to get all or most of the key values out for formatting is to use
 * {@link KeyValuesConsumer}. There is deliberately no low-level, index-based access
 * (something like {@code key(int)}/{@code valueOrNull(int)}) on this interface: that
 * shape only makes sense for an array-backed implementation, and would force every
 * implementation - including ones not backed by a flat array - to expose it anyway.
 *
 * @apiNote The current key values implementation is a simple single String array as the
 * assumption is that most MDC (and or addKeyValuePair) usage is for a couple values and
 * that many formatting usages particularly JSON or headers are to retrieve
 * <strong>all</strong> of the key values. Another assumption for the simplicity is to
 * save memory as there is a potential for many KeyValues with the advent of virtual
 * threads and key values cannot be escaped analysis or garbage collected quickly because
 * they are passed all the way down to the output.
 * <p>
 * The unfortunate consequence of this is that ad-hoc single key lookup or removal is
 * <code>O(n)</code> and not <code>O(1)</code>. If you find this unacceptable with
 * benchmarking because of having many keys and only retrieving a few please file an issue
 * and we may offer alternative map or binary tree implementations in the future.
 */
public sealed interface KeyValues {

	/**
	 * Analogous to {@link Map#get(Object)}
	 * @param key key never <code>null</code>.
	 * @return value for key maybe <code>null</code>.
	 */
	public @Nullable String getValueOrNull(String key);

	/**
	 * The JSON form of the value for a key, added with
	 * {@link MutableKeyValues#putKeyValue(String, String, String)}, usually from an
	 * object passed to a logging facade and turned into JSON by a {@link JsonSerializer}.
	 * Encoders that write JSON can write it in place of the value as a JSON string. JSON
	 * forms are not key values themselves: iteration, {@link #size()} and
	 * {@link #getValueOrNull(String)} do not include them.
	 * @param key key never <code>null</code>.
	 * @return JSON text or <code>null</code> if the key has no JSON form.
	 */
	public @Nullable String getJsonOrNull(String key);

	/**
	 * Used to easily iterate over the key value pairs without using an iterator.
	 * @param action consumer called on each key value pair.
	 */
	@SuppressWarnings("exports")
	void forEach(BiConsumer<? super String, ? super @Nullable String> action);

	/**
	 * Used to easily iterate over the key value pairs without using an iterator.
	 * @param <V> storage type
	 * @param action consumer.
	 * @param counter a pass through integer that can be used for counting purposes or
	 * determining the start perhaps by passing <code>0</code> and then incrementing on
	 * return.
	 * @param storage an extra parameter to avoid unnecessary lambda creation.
	 * @return new value for next counter to be passed to {@link KeyValuesConsumer}.
	 * {@link KeyValuesConsumer#accept(KeyValues, String, String, int, Object)} return.
	 */
	<V> int forEach(KeyValues.KeyValuesConsumer<V> action, int counter, V storage);

	/**
	 * An immutable empty {@link KeyValues}.
	 * @return immutable empty {@link KeyValues}.
	 */
	static KeyValues of() {
		return EmptyKeyValues.INSTANCE;
	}

	/**
	 * Analogous to {@link Map#size()}.
	 * @return size.
	 */
	int size();

	/**
	 * Analogous to {@link Map#isEmpty()}.
	 * @return true if empty.
	 */
	default boolean isEmpty() {
		return size() <= 0;
	}

	/**
	 * A special consumer that avoids lambda garbage.
	 *
	 * @param <V> storage
	 */
	interface KeyValuesConsumer<V> {

		/**
		 * Accepts the KeyValues at the current key and value.
		 * @param kvs kvs.
		 * @param k key.
		 * @param v value.
		 * @param index counter.
		 * @param storage storage like a StringBuidler.
		 * @return custom user count that will be accumulated. If one was returned for
		 * each call then the result would be the total count.
		 */
		public int accept(KeyValues kvs, String k, @Nullable String v, int index, V storage);

	}

	/**
	 * Creates an immutable key values by copying a Map.
	 * @param m map.
	 * @return immutable key values.
	 * @apiNote Be careful when use {@link Map#of()} as its order is non deterministic
	 * because most map implementations are and thus the resulting key values may not be
	 * in the order you expect.
	 */
	public static KeyValues of(Map<String, String> m) {
		if (m.isEmpty())
			return of();
		ArrayKeyValues mdc = new ArrayKeyValues(m.size());
		mdc.putAll(m);
		return mdc.freeze();
	}

	/**
	 * Creates a key values whose entries are the union of two key values, {@code high}
	 * taking precedence over {@code low} on a key collision. The result is an eagerly
	 * built, immutable copy: {@code low} and {@code high} are read once, at the moment of
	 * this call, and are not retained or delegated to afterward. Multiple layers merge by
	 * nesting, e.g. {@code merge(merge(a, b), c)}, the same way a cons list nests -
	 * {@code c} wins over {@code b}, which wins over {@code a}.
	 * @param low lower precedence key values.
	 * @param high higher precedence key values, wins on key collision.
	 * @return {@code high} if {@code low} is empty, {@code low} if {@code high} is empty,
	 * otherwise an immutable copy of both merged together.
	 */
	public static KeyValues merge(KeyValues low, KeyValues high) {
		if (low.isEmpty()) {
			return high;
		}
		if (high.isEmpty()) {
			return low;
		}
		return mergeArrays(low, high);
	}

	/*
	 * low's own keys are already unique (a KeyValues never carries a duplicate), so its
	 * entries never need putKeyValue's duplicate scan the way high's do - only high can
	 * actually collide with what low already contributed. When low is a dense (gap-free)
	 * array-backed KeyValues - true for essentially everything that reaches here in
	 * practice, since merge()'s own output is always dense and neither KeyValues.of(Map)
	 * nor ScopedKeyValues.Builder ever call remove() - its entries are bulk-copied
	 * straight into the new backing array via System.arraycopy rather than appended one
	 * at a time, which is what actually gets this close to a HashMap copy's own cost
	 * rather than just skipping the duplicate scan. A low that is not known dense falls
	 * back to ArrayKeyValues#appendUnchecked (still no duplicate scan, just per-entry
	 * instead of one bulk copy) since a raw copy could otherwise stop at a gap before
	 * reaching real entries that come after it.
	 */
	private static KeyValues mergeArrays(KeyValues low, KeyValues high) {
		ArrayKeyValues buf = new ArrayKeyValues(storedSize(low) + storedSize(high));
		buf.kvs = new @Nullable String[buf.threshold];
		if (low instanceof AbstractArrayKeyValues low2 && low2.dense) {
			System.arraycopy(low2.kvs, 0, buf.kvs, 0, low2.size * 2);
			buf.size = low2.size;
			buf.jsonCount = low2.jsonCount;
		}
		else if (low instanceof AbstractArrayKeyValues low2) {
			low2.forEachEntry(buf::appendUnchecked);
		}
		else {
			low.forEach(buf::appendUnchecked);
		}
		if (high instanceof AbstractArrayKeyValues high2 && high2.jsonCount > 0) {
			/*
			 * Plain values first, which drops any JSON form from low for the keys high
			 * overrides, then high's own JSON forms.
			 */
			high.forEach(buf);
			high2.forEachJsonEntry(buf::putJsonEntry);
		}
		else {
			high.forEach(buf);
		}
		return buf.freeze();
	}

	/*
	 * Includes JSON forms, which take entries of their own.
	 */
	private static int storedSize(KeyValues kvs) {
		return kvs instanceof AbstractArrayKeyValues a ? a.size : kvs.size();
	}

	/**
	 * Copies the KeyValues to a Map. This should be used sparingly.
	 * @return map of kvs.
	 */
	@SuppressWarnings("exports")
	Map<String, @Nullable String> copyToMap();

	/**
	 * Makes the KeyValues thread safe either by copying or returning if already
	 * immutable.
	 * @return immutable key values.
	 */
	public KeyValues freeze();

	/**
	 * KeyValues that can be updated.
	 */
	public sealed interface MutableKeyValues extends KeyValues, BiConsumer<String, @Nullable String> {

		/**
		 * Copies.
		 * @return kvs copy.
		 */
		MutableKeyValues copy();

		/**
		 * Same as {@link Map#put(Object, Object)}.
		 * @param key key.
		 * @param value value maybe <code>null</code>.
		 */
		void putKeyValue(String key, @Nullable String value);

		/**
		 * Puts a value that also has a JSON form, see
		 * {@link KeyValues#getJsonOrNull(String)}. Putting the key again with
		 * {@link #putKeyValue(String, String)} or removing it removes the JSON form.
		 * @param key key.
		 * @param value plain text value used by encoders that do not write JSON.
		 * @param json JSON text or <code>null</code> for no JSON form.
		 */
		void putKeyValue(String key, @Nullable String value, @Nullable String json);

		/**
		 * Puts an object a logging facade was given as a key value. A string or
		 * <code>null</code> is put as is. Anything else is put as its
		 * {@link Object#toString()} and, when a serializer is given, the JSON form the
		 * serializer returns. A serializer that throws only loses the JSON form.
		 * @param key key.
		 * @param value value maybe <code>null</code>.
		 * @param serializer usually {@link JsonSerializer#of(LogConfig)}, maybe
		 * <code>null</code> for plain text only.
		 */
		default void putObject(String key, @Nullable Object value, @Nullable JsonSerializer serializer) {
			if (value == null || value instanceof String) {
				putKeyValue(key, (String) value);
				return;
			}
			String plain = value.toString();
			if (serializer == null) {
				putKeyValue(key, plain);
				return;
			}
			String json;
			try {
				json = serializer.toJson(value);
			}
			catch (RuntimeException e) {
				json = null;
			}
			putKeyValue(key, plain, json);
		}

		/**
		 * Fluent version of put.
		 * @param key key.
		 * @param value value maybe <code>null</code>.
		 * @return this.
		 */
		default MutableKeyValues add(String key, @Nullable String value) {
			putKeyValue(key, value);
			return this;
		}

		/**
		 * Same as {@link Map#remove(Object)}.
		 * @param key key.
		 */
		void remove(String key);

		/**
		 * Same as {@link #putKeyValue(String, String)} but will ignore null keys from
		 * sources that have null keys.
		 */
		@Override
		default void accept(@Nullable String t, @Nullable String u) {
			if (t == null) {
				return;
			}
			putKeyValue(t, u);
		}

		/**
		 * Same as {@link Map#putAll(Map)}.
		 * @param m map.
		 */
		default void putAll(Map<String, ? extends @Nullable String> m) {
			m.forEach(this);
		}

		/**
		 * Copies a map into {@link MutableKeyValues}.
		 * @param m map.
		 * @return key values
		 */
		public static MutableKeyValues of(Map<String, String> m) {
			int size = m.size();
			var kvs = of(size == 0 ? 4 : size);
			kvs.putAll(m);
			return kvs;
		}

		/**
		 * Creates mutable keys with pre-allocation.
		 * @param size should be greater than 0.
		 * @return mutable keys.
		 */
		public static MutableKeyValues of(int size) {
			return new ArrayKeyValues(size);
		}

		/**
		 * Empty mutable keys with default pre-allocation.
		 * @return mutable keys.
		 */
		public static MutableKeyValues of() {
			return new ArrayKeyValues();
		}

	}

	/**
	 * Turns an object a logging facade was given as a key value into JSON text when it is
	 * added, on the logging thread, so key values stay immutable strings. Only used when
	 * {@value LogProperties#KEY_VALUES_JSON_PROPERTY} is on.
	 * <p>
	 * A serializer registered in {@link LogConfig#serviceRegistry()} under this class is
	 * used, otherwise the {@linkplain #of() built in} one.
	 */
	@FunctionalInterface
	public interface JsonSerializer {

		/**
		 * Converts a value to JSON.
		 * @param value never a {@link String}, since strings are only ever plain text.
		 * @return JSON text, or <code>null</code> to keep only the plain text form.
		 */
		@Nullable String toJson(Object value);

		/**
		 * The built in serializer. It writes numbers and booleans as JSON numbers and
		 * booleans, maps as objects, iterables and arrays as arrays, and anything else as
		 * a JSON string of its {@link Object#toString()}. Non finite floating point
		 * numbers are written as strings.
		 * @return built in serializer.
		 */
		static JsonSerializer of() {
			return BuiltinJsonSerializer.INSTANCE;
		}

		/**
		 * The serializer a logging facade should use for a config.
		 * @param config config.
		 * @return serializer, or <code>null</code> when
		 * {@value LogProperties#KEY_VALUES_JSON_PROPERTY} is off.
		 */
		static @Nullable JsonSerializer of(LogConfig config) {
			if (config.globals().keyValuesJson() == LogConfig.Globals.KeyValuesJson.OFF) {
				return null;
			}
			var registered = config.serviceRegistry().findOrNull(JsonSerializer.class);
			return registered == null ? of() : registered;
		}

	}

	/**
	 * Pretty prints key values.
	 * @param kvs kvs.
	 * @param sb StringBuilder.
	 */
	public static void prettyPrint(KeyValues kvs, StringBuilder sb) {
		sb.append('{');
		kvs.forEach(AbstractArrayKeyValues.toStringer, 0, sb);
		sb.append('}');
	}

	/**
	 * Checks if two {@link KeyValues} are equal.
	 * @param self first.
	 * @param kvs second.
	 * @return true if equal.
	 */
	@SuppressWarnings("ReferenceEquality")
	static boolean equals(KeyValues self, KeyValues kvs) {
		if (self == kvs)
			return true;
		if (kvs.size() != self.size()) {
			return false;
		}
		boolean[] result = { true };
		self.forEach((k, v) -> {
			if (result[0] && (!Objects.equals(v, kvs.getValueOrNull(k))
					|| !Objects.equals(self.getJsonOrNull(k), kvs.getJsonOrNull(k)))) {
				result[0] = false;
			}
		});
		return result[0];
	}

}

final class EmptyKeyValues implements KeyValues {

	static final EmptyKeyValues INSTANCE = new EmptyKeyValues();

	private EmptyKeyValues() {
	}

	@Override
	public @Nullable String getValueOrNull(String key) {
		return null;
	}

	@Override
	public @Nullable String getJsonOrNull(String key) {
		return null;
	}

	@Override
	public void forEach(BiConsumer<? super String, ? super String> action) {
	}

	@Override
	public <V> int forEach(KeyValuesConsumer<V> action, int index, V storage) {
		return index;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public Map<String, @Nullable String> copyToMap() {
		return Map.of();
	}

	@Override
	public KeyValues freeze() {
		return this;
	}

	@Override
	public boolean equals(@Nullable Object keyValues) {
		if (keyValues instanceof KeyValues kvs) {
			return kvs.isEmpty();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return 1;
	}

	@Override
	public String toString() {
		return "{}";
	}

}

sealed abstract class AbstractArrayKeyValues implements KeyValues {

	protected static final KeyValuesConsumer<StringBuilder> toStringer = (kvs, k, v, i, b) -> {
		if (i > 0) {
			b.append(',').append(' ');
		}
		b.append('"').append(k).append('"').append(':').append('"').append(v).append('"');
		return i + 1;
	};

	protected static final int DEFAULT_INITIAL_CAPACITY = 2;

	protected static final String[] EMPTY = new String[] {};

	protected @Nullable String[] kvs;

	protected int size;

	protected int threshold;

	/*
	 * True as long as no MutableKeyValues#remove(String) has ever left a gap in kvs -
	 * KeyValues.merge's fast path bulk-copies another instance's raw kvs array via
	 * System.arraycopy only when this is true, since that skips the usual "stop at the
	 * first empty slot" scan and would otherwise risk stopping at a gap before reaching
	 * real entries that come after it. Defaults true (every fresh instance starts gap
	 * free) and is propagated by copy()/freeze() below, since those also copy kvs
	 * verbatim, gaps and all.
	 */
	protected boolean dense = true;

	/*
	 * JSON forms (KeyValues#getJsonOrNull) are stored as extra entries whose key is the
	 * plain key prefixed with JSON_PREFIX, a character no real key starts with. jsonCount
	 * is how many of the size entries are JSON forms, so the common case of none costs
	 * one int check when iterating.
	 */
	protected static final char JSON_PREFIX = '\u0000';

	protected int jsonCount;

	protected AbstractArrayKeyValues(@Nullable String[] kvs, int size, int threshold) {
		super();
		this.kvs = kvs;
		this.size = size;
		this.threshold = threshold;
	}

	@Override
	public int size() {
		return this.size - this.jsonCount;
	}

	static boolean isJsonKey(String k) {
		return !k.isEmpty() && k.charAt(0) == JSON_PREFIX;
	}

	private static boolean isJsonKeyOf(String k, String key) {
		return k.length() == key.length() + 1 && k.charAt(0) == JSON_PREFIX && k.regionMatches(1, key, 0, key.length());
	}

	@Override
	public @Nullable String getJsonOrNull(String key) {
		if (jsonCount == 0) {
			return null;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			if (k != null && isJsonKeyOf(k, key)) {
				return kvs[i + 1];
			}
		}
		return null;
	}

	/*
	 * Every stored entry including JSON forms, for copying into another instance.
	 */
	void forEachEntry(BiConsumer<String, @Nullable String> action) {
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			if (k != null) {
				action.accept(k, kvs[i + 1]);
			}
		}
	}

	void forEachJsonEntry(BiConsumer<String, @Nullable String> action) {
		if (jsonCount == 0) {
			return;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			if (k != null && isJsonKey(k)) {
				action.accept(k, kvs[i + 1]);
			}
		}
	}

	@Override
	public @Nullable String getValueOrNull(String key) {
		if (size == 0) {
			return null;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			/*
			 * get
			 */
			if (Objects.equals(k, key)) {
				return kvs[i + 1];
			}
		}
		return null;
	}

	@Override
	public void forEach(BiConsumer<? super String, ? super @Nullable String> action) {
		if (size == 0) {
			return;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			var v = kvs[i + 1];
			if (k == null || (jsonCount > 0 && isJsonKey(k))) {
				continue;
			}
			action.accept(k, v);
		}
	}

	@Override
	public <V> int forEach(KeyValuesConsumer<V> action, int index, V storage) {
		if (size == 0) {
			return index;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			var v = kvs[i + 1];
			if (k == null || (jsonCount > 0 && isJsonKey(k))) {
				continue;
			}
			index = action.accept(this, k, v, index, storage);
		}

		return index;

	}

	@Override
	public Map<String, @Nullable String> copyToMap() {
		final Map<String, @Nullable String> result = new HashMap<>(size);
		forEach(result::put);
		return result;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		KeyValues.prettyPrint(this, sb);
		return sb.toString();
	}

	protected static int calculateInitialThreshold(final int initialCapacity) {
		if (initialCapacity < 0) {
			throw new IllegalArgumentException("Initial capacity must be at least zero but was " + initialCapacity);
		}
		return 2 * ceilingNextPowerOfTwo(initialCapacity == 0 ? 1 : initialCapacity);
	}

	/**
	 * Calculate the next power of 2, greater than or equal to x.
	 * <p>
	 * From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
	 * @param x Value to round up
	 * @return The next power of 2 from x inclusive
	 */
	protected static int ceilingNextPowerOfTwo(final int x) {
		final int BITS_PER_INT = 32;
		return 1 << (BITS_PER_INT - Integer.numberOfLeadingZeros(x - 1));
	}

	@Override
	public int hashCode() {
		int[] result = { 1 };
		forEach((k, v) -> {
			result[0] = result[0] + k.hashCode();
			result[0] = result[0] + (v == null ? 0 : v.hashCode());
		});
		return result[0];
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj instanceof KeyValues kvs) {
			return KeyValues.equals(this, kvs);
		}
		return false;
	}

}

final class ImmutableArrayKeyValues extends AbstractArrayKeyValues {

	@SuppressWarnings("null")
	ImmutableArrayKeyValues(String[] kvs, int size, int threshold) {
		super(kvs, size, threshold);
	}

	@Override
	public KeyValues freeze() {
		return this;
	}

}

/*
 * A KeyValues that uses a single string array for memory savings which is desirable in
 * the case of loom where there could be several orders of key values instances at any
 * given time.
 */
final class ArrayKeyValues extends AbstractArrayKeyValues implements MutableKeyValues {

	public ArrayKeyValues() {
		this(DEFAULT_INITIAL_CAPACITY);
	}

	@SuppressWarnings("null")
	public ArrayKeyValues(final int initialCapacity) {
		this(EMPTY, 0, calculateInitialThreshold(initialCapacity));
	}

	private ArrayKeyValues(@Nullable String[] kvs, int size, int threshold) {
		super(kvs, size, threshold);
		this.kvs = kvs;
		this.size = size;
		this.threshold = threshold;
	}

	@Override
	public MutableKeyValues copy() {
		if (size == 0) {
			return new ArrayKeyValues();
		}
		ArrayKeyValues orig = this;
		@Nullable String[] copyKvs = new @Nullable String[this.threshold];
		System.arraycopy(orig.kvs, 0, copyKvs, 0, orig.threshold);
		var result = new ArrayKeyValues(copyKvs, size, threshold);
		result.dense = this.dense;
		result.jsonCount = this.jsonCount;
		return result;
	}

	@SuppressWarnings("null") // Eclipse bug
	@Override
	public KeyValues freeze() {
		if (size == 0) {
			return KeyValues.of();
		}
		ArrayKeyValues orig = this;
		String[] copyKvs = new String[this.threshold];
		System.arraycopy(orig.kvs, 0, copyKvs, 0, orig.threshold);
		var result = new ImmutableArrayKeyValues(copyKvs, size, threshold);
		result.dense = this.dense;
		result.jsonCount = this.jsonCount;
		return result;
	}

	public @Nullable String apply(String t) {
		return getValueOrNull(t);
	}

	/*
	 * We implement BiConsumer to avoid garbage like entry set and iterators
	 */
	@Override
	public void putKeyValue(String key, @Nullable String value) {
		put(key, value);
		if (jsonCount > 0 && !isJsonKey(key)) {
			remove(JSON_PREFIX + key);
		}
	}

	@Override
	public void putKeyValue(String key, @Nullable String value, @Nullable String json) {
		putKeyValue(key, value);
		if (json != null) {
			put(JSON_PREFIX + key, json);
		}
	}

	/*
	 * A JSON form entry as stored in another instance, key already prefixed.
	 */
	void putJsonEntry(String jsonKey, @Nullable String json) {
		put(jsonKey, json);
	}

	@SuppressWarnings("ReferenceEquality")
	private void put(String key, @Nullable String value) {
		Objects.requireNonNull(key);
		if (kvs == EMPTY) {
			inflateTable(threshold);
		}

		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			/*
			 * replacement
			 */
			if (Objects.equals(k, key)) {
				kvs[i + 1] = value;
				return;
			}
			else if (k == null && kvs[i + 1] == null) {
				kvs[i] = key;
				kvs[i + 1] = value;
				size++;
				countIfJson(key);
				return;
			}
		}
		ensureCapacity();
		size++;
		countIfJson(key);
		int valueIndex = (size * 2) - 1;
		kvs[valueIndex - 1] = key;
		kvs[valueIndex] = value;
	}

	/*
	 * putKeyValue's leading scan exists to find/overwrite an existing key, so calling it
	 * size() times to copy in an already-duplicate-free KeyValues costs 1+2+...+size()
	 * comparisons - O(size()^2). Skipping straight to the append tail is safe only when
	 * the caller already knows key cannot be a duplicate, e.g. copying another KeyValues'
	 * entries (unique by construction) one at a time into a buffer that started empty -
	 * used as KeyValues#merge's fallback for a low side that is not dense
	 * (System.arraycopy is unsafe there - see the `dense` field), which is otherwise the
	 * same shape as putKeyValue's own append tail.
	 */
	@SuppressWarnings("ReferenceEquality")
	void appendUnchecked(String key, @Nullable String value) {
		if (kvs == EMPTY) {
			inflateTable(threshold);
		}
		ensureCapacity();
		size++;
		countIfJson(key);
		int valueIndex = (size * 2) - 1;
		kvs[valueIndex - 1] = key;
		kvs[valueIndex] = value;
	}

	private void countIfJson(String key) {
		if (isJsonKey(key)) {
			jsonCount++;
		}
	}

	@Override
	@SuppressWarnings("ReferenceEquality")
	public void remove(final String key) {
		if (kvs == EMPTY) {
			return;
		}
		boolean json = isJsonKey(key);
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			/*
			 * remove
			 */
			if (Objects.equals(k, key)) {
				kvs[i] = null;
				kvs[i + 1] = null;
				size--;
				dense = false;
				if (json) {
					jsonCount--;
				}
			}
		}
		if (!json && jsonCount > 0) {
			remove(JSON_PREFIX + key);
		}
	}

	private void ensureCapacity() {
		if ((size * 2) >= threshold) {
			resize(threshold * 2);
		}
	}

	private void resize(final int newCapacity) {
		final @Nullable String[] oldKvs = kvs;

		kvs = new @Nullable String[newCapacity];

		System.arraycopy(oldKvs, 0, kvs, 0, (size * 2));

		threshold = newCapacity;
	}

	/**
	 * Inflates the table.
	 */
	private void inflateTable(final int toSize) {
		threshold = toSize;
		kvs = new @Nullable String[toSize];
	}

}
