package io.jstach.rainbowgum;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * Key Value Pairs similar to a {@code Map<String,String>} but optimized for memory and
 * less garbage (no iterators).
 * <p>
 * <strong>The integer index used in the low level access are not in array order and thus
 * {@link #start()} and {@link #next(int)} should be used to iterate over the key values
 * instead of manually incrementing.</strong>
 * <p>
 * The observed order of the current default Key Value Pairs is insertion based ordering
 * but that should not be relied on as other implementations may change that.
 * <p>
 * The preferred way to get all or most of the key values out for formatting is to use
 * {@link KeyValuesConsumer}.
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
	 * Low-level key access.
	 * @param i index from {@link #start()} or {@link #next(int)}.
	 * @return key.
	 * @throws IndexOutOfBoundsException if the index is not valid for this key values.
	 */
	public String key(int i) throws IndexOutOfBoundsException;

	/**
	 * Low-level key access.
	 * @param i index from {@link #start()} or {@link #next(int)}.
	 * @return key.
	 * @throws IndexOutOfBoundsException if the index is not valid for this key values.
	 */
	public @Nullable String valueOrNull(int i) throws IndexOutOfBoundsException;

	/**
	 * Returns the index of the first key.
	 * @return the start index which may or may not be zero.
	 */
	public int start();

	/**
	 * Gets the next key index from the passed in previous key index.
	 * @param i the previous key index.
	 * @return next key index.
	 */
	public int next(int i);

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
	 * @apiNote Be careful when use {@link Map#of()} as its order is non deterministic and
	 * thus the resulting key values may not be in the order you expect.
	 */
	public static KeyValues of(Map<String, String> m) {
		ArrayKeyValues mdc = new ArrayKeyValues(m.size());
		mdc.putAll(m);
		return mdc;
	}

	/**
	 * Copies the KeyValues to a Map. This should be used sparingly.
	 * @return map of kvs.
	 */
	@SuppressWarnings({"exports", "NullAway"}) //TODO NullAway bug
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
	@SuppressWarnings("NullAway") // TODO NullAway bug
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
		default void putAll(Map<String, String> m) {
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
	static boolean equals(KeyValues self, KeyValues kvs) {
		if (self == kvs)
			return true;
		if (kvs.size() != self.size()) {
			return false;
		}
		for (int i = self.start(); i > -1; i = self.next(i)) {
			var k = self.key(i);
			var v = self.valueOrNull(i);
			if (!Objects.equals(v, kvs.getValueOrNull(k))) {
				return false;
			}
		}
		return true;
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
	public int next(int index) {
		return -1;
	}

	@Override
	public String key(int index) {
		throw new IndexOutOfBoundsException(index);
	}

	@Override
	public @Nullable String valueOrNull(int index) {
		throw new IndexOutOfBoundsException(index);
	}

	@Override
	public int start() {
		return -1;
	}

	@Override
	@SuppressWarnings("NullAway") // TODO NullAway bug
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

	protected AbstractArrayKeyValues(@Nullable String[] kvs, int size, int threshold) {
		super();
		this.kvs = kvs;
		this.size = size;
		this.threshold = threshold;
	}

	@Override
	public String key(int index) {
		if (index >= (threshold - 1)) {
			throw new IndexOutOfBoundsException(index);
		}
		String k = kvs[index];
		if (k == null) {
			throw new IndexOutOfBoundsException(index);
		}
		return k;
	}

	@Override
	public @Nullable String valueOrNull(int index) {
		if (index >= (threshold - 1)) {
			throw new IndexOutOfBoundsException(index);
		}
		return kvs[index + 1];
	}

	@Override
	public int size() {
		return this.size;
	}

	private int _next(int index) {
		if (size == 0) {
			return -1;
		}
		var limit = threshold - 1;
		if (index >= limit) {
			return -1;
		}
		int i = index;
		for (; i < limit; i += 2) {
			var k = kvs[i];
			var v = kvs[i + 1];
			if (k == null && v == null) {
				continue;
			}
			return i;
		}
		return -1;
	}

	@Override
	public int next(int index) {
		return _next(index + 2);
	}

	@Override
	public int start() {
		return _next(0);
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
			if (k == null) {
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
			if (k == null) {
				continue;
			}
			index = action.accept(this, k, v, index, storage);
		}

		return index;

	}

	@Override
	@SuppressWarnings("NullAway") // TODO NullAway bug
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
		int result = 1;
		for (int i = start(); i > -1; i = next(i)) {
			var k = key(i);
			var v = valueOrNull(i);
			result = result + k.hashCode();
			result = result + (v == null ? 0 : v.hashCode());
		}
		return result;
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
		@Nullable
		String[] copyKvs = new @Nullable String[this.threshold];
		System.arraycopy(orig.kvs, 0, copyKvs, 0, orig.threshold);
		return new ArrayKeyValues(copyKvs, size, threshold);
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
		return new ImmutableArrayKeyValues(copyKvs, size, threshold);
	}

	public @Nullable String apply(String t) {
		return getValueOrNull(t);
	}

	/*
	 * We implement BiConsumer to avoid garbage like entry set and iterators
	 */
	@Override
	public void putKeyValue(String key, @Nullable String value) {
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
				return;
			}
		}
		ensureCapacity();
		size++;
		int valueIndex = (size * 2) - 1;
		kvs[valueIndex - 1] = key;
		kvs[valueIndex] = value;
	}

	@Override
	public void remove(final String key) {
		if (kvs == EMPTY) {
			return;
		}
		for (int i = 0; i < (threshold - 1); i += 2) {
			var k = kvs[i];
			/*
			 * remove
			 */
			if (Objects.equals(k, key)) {
				kvs[i] = null;
				kvs[i + 1] = null;
				size--;
			}
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