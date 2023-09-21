package io.jstach.rainbowgum;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

public interface KeyValues {
	
	public @Nullable String getValue(String key);
	
	public @Nullable String key(int i);
	
	public @Nullable String value(int i);
	
	public int start();
	
	public int next(int i);
	
	void forEach(
			BiConsumer<? super String, ? super String> action);
	
	<V> int forEach(
			KeyValues.KeyValuesConsumer<V> action, int index, V storage);
	
	static KeyValues of() {
		return EmptyKeyValues.INSTANCE;
	}
	
	int size();
	
	default boolean isEmpty() {
		return size() <= 0;
	}
	
	interface KeyValuesConsumer<V> {
		public int accept(KeyValues values, String key, String value, int index, V storage);
	}
	
	public static KeyValues of(Map<String,String> m) {
		ArrayKeyValues mdc = new ArrayKeyValues(m.size());
		mdc.putAll(m);
		return mdc;
	}

	Map<String, String> copyToMap();
	
	public interface MutableKeyValues extends KeyValues, BiConsumer<String, String> {

		MutableKeyValues copy();

		void putKeyValue(
				String key,
				String value);

		void remove(
				String key);
		

		@Override
		default void accept(
				String t,
				String u) {
			putKeyValue(t, u);
		}
		
		default void putAll(Map<String, String> m) {
			m.forEach(this);
		}
		
		public static MutableKeyValues of(int size) {
			return new ArrayKeyValues(size);
		}
		
		public static MutableKeyValues of() {
			return new ArrayKeyValues();
		}
	}

}
/*
 * A KeyValues that uses a single string array for memory savings which is desirable
 * in the case of loom where there could be several orders of keyvalues instances
 * at any given time.
 */
class ArrayKeyValues implements BiConsumer<String, String>, Function<String,String>, MutableKeyValues {

	private static final int DEFAULT_INITIAL_CAPACITY = 4;

	private static final String[] EMPTY = new String[] {};

	private String[] kvs = EMPTY;

	private int size = 0;

	private int threshold;

	public ArrayKeyValues() {
		this(
				DEFAULT_INITIAL_CAPACITY);
	}

	public ArrayKeyValues(
			final int initialCapacity) {
		if (initialCapacity < 0) {
			throw new IllegalArgumentException("Initial capacity must be at least zero but was " + initialCapacity);
		}
		threshold = ceilingNextPowerOfTwo(initialCapacity == 0 ? 1 : initialCapacity);
	}
	
	
	private ArrayKeyValues(
			String[] kvs,
			int size,
			int threshold) {
		super();
		this.kvs = kvs;
		this.size = size;
		this.threshold = threshold;
	}
	
	@Override
	public int size() {
		return this.size;
	}

	public MutableKeyValues copy() {
		ArrayKeyValues orig = this;
		String[] copyKvs = new String[this.threshold];
		System.arraycopy(orig.kvs, 0, copyKvs, 0, orig.size * 2);
		return new ArrayKeyValues(copyKvs, size, threshold);
	}

	
	@Override
	public @Nullable String key(
			int index) {
		if (index >= (threshold - 1)) {
			throw new IndexOutOfBoundsException(index);
		}
		return kvs[index];
	}
	
	@Override
	public @Nullable String value(
			int index) {
		if (index >= (threshold - 1)) {
			throw new IndexOutOfBoundsException(index);
		}
		return kvs[index + 1];
	}
	
	 private int _next(
			int index) {
		var limit = threshold - 1;
		if (index >= limit) {
			return -1;
		}
		int i = index;
		for (; i < limit; i+=2 ) {
			var k = kvs[i];
			var v = kvs[i+1];
			if (k == null && v == null) {
				continue;
			}
		}
		return i;
	}
	 
	 @Override
	public int next(
			int index) {
		 return _next(index + 2);
	}
	 
	 @Override
	public int start() {
		 return _next(0);
	}
	
	 
	 @Override
	public String apply(
			String t) {
		 return getValue(t);
	}
	
	 @Override
	public void accept(
			String t,
			String u) {
		putKeyValue(t, u);
	}
	 
	/*
	 * We implement BiConsumer to avoid garbage
	 * like entry set and iterators
	 */
	@Override
	public void putKeyValue(
			String key,
			String value) {
		if (kvs == EMPTY) {
			inflateTable(threshold);
		}
		if (Objects.isNull(key)) {
			return;
		}
		for (int i = 0; i < size * 2; i += 2) {
			var k = kvs[i];
			/*
			 * replacement
			 */
			if (Objects.equals(k, key)) {
				kvs[i + 1] = value;
				return;
			}
			else if (k == null && kvs[i+1] == null) {
				kvs[i] = k;
				kvs[i+1] = value;
			}
		}
		ensureCapacity();
		int valueIndex = (size * 2);
		kvs[valueIndex - 1] = key;
		kvs[valueIndex] = value;
		return;
	}
	
	@Override
	public String getValue(
			String key) {
		for (int i = 0; i < size * 2; i += 2) {
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

	public void forEach(
			BiConsumer<? super String, ? super String> action) {
		for (int i = 0; i < size * 2; i += 2) {
			var k = kvs[i];
			var v = kvs[i+1];
			if (k == null && v == null) {
				continue;
			}
			action.accept(k, v);
		}
	}
	
	public void forEachKey(Consumer<? super String> consumer) {
		for (int i = 0; i < size * 2; i += 2) {
			var k = kvs[i];
			if (k == null && kvs[i+1] == null) {
				continue;
			}
			consumer.accept(k);
		}
	}
	
	@Override
	public <V> int forEach(
			KeyValuesConsumer<V> action, int index, V storage) {
		
		for (int i = 0; i < size * 2; i += 2) {
			var k = kvs[i];
			var v = kvs[i+1];
			if (k == null && kvs[i+1] == null) {
				continue;
			}
			index = action.accept(this, k, v, index, storage);
		}
		
		return index;
		
	}
	
	public void putAll(Map<String,String> m) {
		m.forEach(this);
	}
	
	@Override
	public void remove(
			final String key) {
		if (kvs == EMPTY) {
			return;
		}
		for (int i = 0; i < size * 2; i += 2) {
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

	@Override
	public Map<String, String> copyToMap() {
		final Map<String, String> result = new HashMap<>(size);
		for (int i = 0; i < size; i += 2) {
			String k = kvs[i];
			String v = kvs[i + 1];
			if (k == null || v == null) {
				continue;
			}
			result.put(k, v);
		}
		return result;
	}

	private void ensureCapacity() {
		if ((size * 2) >= threshold) {
			resize(threshold * 2);
		}
	}

	private void resize(
			final int newCapacity) {
		final String[] oldKvs = kvs;

		kvs = new String[newCapacity];

		System.arraycopy(oldKvs, 0, kvs, 0, (size * 2));

		threshold = newCapacity;
	}

	/**
	 * Inflates the table.
	 */
	private void inflateTable(
			final int toSize) {
		threshold = toSize;
		kvs = new String[toSize];
	}

	/**
	 * Calculate the next power of 2, greater than or equal to x.
	 * <p>
	 * From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
	 *
	 * @param x
	 *              Value to round up
	 * @return The next power of 2 from x inclusive
	 */
	private static int ceilingNextPowerOfTwo(
			final int x) {
		final int BITS_PER_INT = 32;
		return 1 << (BITS_PER_INT - Integer.numberOfLeadingZeros(x - 1));
	}

}