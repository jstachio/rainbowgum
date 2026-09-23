package io.jstach.rainbowgum.benchmark.jmh;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * Compares RainbowGum's real {@link KeyValues#merge(KeyValues, KeyValues)} against two
 * "pretend" approaches modeled directly on
 * <a href="https://github.com/qos-ch/logback-scoped-mdc">logback-scoped-mdc</a>'s
 * {@code ScopedMDC}: every push there does {@code new LinkedHashMap<>(current)} plus the
 * new entries, before ever binding the {@code ScopedValue}. {@link LinkedHashMap}, not
 * plain {@link java.util.HashMap}, is the realistic comparison here: {@link KeyValues}
 * itself preserves insertion order, so a {@code Map}-based fallback would need to as well
 * to actually be a drop-in replacement, not just a same-shape stand-in.
 * {@code KeyValues.merge} is itself an eager copy (a plain array-backed flatten, not a
 * lazy layered view - it used to be a lazy view but that had a real, since-fixed
 * correctness bug at deep nesting, see {@code KeyValuesMergeTest}), so all three variants
 * measured here are actually the same shape of work: copy what is currently bound plus
 * the new layer's entries into a fresh, immutable structure on every push. What differs
 * is the target data structure - a flat array ({@link KeyValues}), a
 * {@link LinkedHashMap}, or an immutable {@link Map#copyOf(Map)}.
 * <p>
 * Each variant's new layer is built once per push via that variant's own native
 * construction path, not measured against some other variant's shape: the
 * {@link KeyValues} layer is written straight into a {@link MutableKeyValues} via
 * {@link MutableKeyValues#putKeyValue(String, String)}, the same way {@code
 * ScopedKeyValues.Builder} itself does it now (no {@link Map} is built or copied to get
 * there); the two {@link Map}-backed variants build a plain {@link Map} the way a real
 * caller populating one naturally would. An earlier version of this benchmark built every
 * layer as a {@link Map} first and only converted the {@code KeyValues} variant's layer
 * to {@link KeyValues} inside the timed {@code pushOneMore} method itself - that charged
 * {@code keyValuesPushOneMore} for a {@code Map}-to-{@code KeyValues} copy no production
 * code path actually pays anymore.
 * <p>
 * The immutable-map variant exists only because null values are no longer allowed in
 * {@code ScopedKeyValues} (see its own javadoc) - that guarantee is what makes
 * {@link Map#copyOf(Map)} usable at all here (it rejects null keys/values), and immutable
 * map construction/lookup is exactly the kind of thing a future JDK could plausibly get a
 * lot faster at (compact identity, specialized small-map internals) in a way a plain
 * mutable {@link LinkedHashMap} copy would not automatically benefit from.
 * <p>
 * Two things are measured per variant: the cost of one more push on top of an
 * already-{@code depth}-deep structure (the steady-state "entering one more nested scope"
 * cost, proportional to however many entries have accumulated by then), and lookup cost
 * for a key that only exists in the first-pushed layer versus one that only exists in the
 * last-pushed (most recent) layer. {@link KeyValues} is documented as <code>O(n)</code>
 * for a single lookup (a plain array, optimized for "iterate everything" over "look up
 * one key") and {@code merge} appends each new layer's entries after whatever was already
 * accumulated, so a first-pushed key resolves in a short scan near the front of the array
 * while a last-pushed key requires scanning past every earlier entry first - the opposite
 * of what "first pushed" versus "last pushed" would suggest if this were still the old
 * lazy, most-recent-layer-checked-first composite. The two {@link Map}-backed variants
 * are close to {@code O(1)} regardless of which layer a key came from.
 * <p>
 * A fourth variant, log4j2's {@link SortedArrayStringMap}, is included because it is the
 * closest real-world relative of {@link KeyValues}: also a flat two-array (keys/values)
 * structure with no per-entry heap object, but sorted by key and searched with
 * {@code Arrays.binarySearch} rather than insertion-ordered and linearly scanned. It is
 * expected to lose on push (its copy constructor bulk-copies via {@code
 * System.arraycopy} same as {@link KeyValues}, but merging in a new layer still needs an
 * {@code Arrays.binarySearch} plus an array-shifting insert per new key to keep the
 * result sorted) and win on lookup ({@code O(log n)} regardless of a key's position,
 * unlike {@link KeyValues}'s position-dependent linear scan). Its own {@code freeze()} is
 * not used here - it just flips a plain, non-{@code volatile} {@code boolean} field on
 * the same mutable object in place, not remotely the same guarantee as
 * {@link KeyValues#freeze()}'s real copy-to-immutable.
 * <p>
 * Run with: {@code mvn -pl benchmark/rainbowgum-benchmark-jmh-scoped-key-values -am
 * package} then {@code java --add-opens java.base/java.util=ALL-UNNAMED -cp
 * "benchmark/rainbowgum-benchmark-jmh-scoped-key-values/target/classes:$(find ~/.m2
 * -name 'jmh-core-*.jar' -o -name 'jopt-simple-*.jar' -o -name 'commons-math3-*.jar' |
 * tr '\n' ':')core/target/classes" org.openjdk.jmh.Main}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ScopedKeyValuesLookupBenchmark {

	@Param({ "1", "4", "16", "64" })
	public int depth;

	@Param({ "1", "4" })
	public int keysPerLayer;

	private KeyValues keyValues;

	private Map<String, String> eagerLinkedHashMap;

	private Map<String, String> eagerImmutableMap;

	private Map<String, String> lastLayer;

	private KeyValues lastKeyValuesLayer;

	private SortedArrayStringMap sortedArrayStringMap;

	private SortedArrayStringMap lastSortedArrayStringMapLayer;

	private String firstInsertedKey;

	private String lastInsertedKey;

	@Setup(org.openjdk.jmh.annotations.Level.Trial)
	public void setup() {
		KeyValues c = KeyValues.of();
		Map<String, String> eagerLinkedHash = Map.of();
		Map<String, String> eagerImmutable = Map.of();
		SortedArrayStringMap sortedArray = new SortedArrayStringMap();
		Map<String, String> firstLayer = null;
		for (int i = 0; i < depth; i++) {
			Map<String, String> layer = layer(i, keysPerLayer);
			KeyValues keyValuesLayer = keyValuesLayer(i, keysPerLayer);
			SortedArrayStringMap sortedArrayLayer = sortedArrayStringMapLayer(i, keysPerLayer);
			if (firstLayer == null) {
				firstLayer = layer;
			}
			this.lastLayer = layer;
			this.lastKeyValuesLayer = keyValuesLayer;
			this.lastSortedArrayStringMapLayer = sortedArrayLayer;
			c = KeyValues.merge(c, keyValuesLayer);
			eagerLinkedHash = eagerLinkedHashMerge(eagerLinkedHash, layer);
			eagerImmutable = eagerImmutableMerge(eagerImmutable, layer);
			sortedArray = sortedArrayStringMapMerge(sortedArray, sortedArrayLayer);
		}
		this.keyValues = c;
		this.eagerLinkedHashMap = eagerLinkedHash;
		this.eagerImmutableMap = eagerImmutable;
		this.sortedArrayStringMap = sortedArray;
		this.firstInsertedKey = firstLayer == null ? "missing" : firstLayer.keySet().iterator().next();
		this.lastInsertedKey = lastLayer == null ? "missing" : lastLayer.keySet().iterator().next();
	}

	private static Map<String, String> layer(int layerIndex, int keysPerLayer) {
		Map<String, String> layer = new LinkedHashMap<>();
		for (int k = 0; k < keysPerLayer; k++) {
			layer.put("k" + layerIndex + "-" + k, "v" + layerIndex + "-" + k);
		}
		return layer;
	}

	/**
	 * Builds a layer the same way {@code ScopedKeyValues.Builder} does now: writing
	 * straight into a {@link MutableKeyValues}, never through a {@link Map}.
	 */
	private static KeyValues keyValuesLayer(int layerIndex, int keysPerLayer) {
		var buf = MutableKeyValues.of(keysPerLayer);
		for (int k = 0; k < keysPerLayer; k++) {
			buf.putKeyValue("k" + layerIndex + "-" + k, "v" + layerIndex + "-" + k);
		}
		return buf.freeze();
	}

	/**
	 * Mirrors {@code ScopedMDC.putAll}: a full copy of whatever is currently bound, plus
	 * the new layer's entries.
	 */
	private static Map<String, String> eagerLinkedHashMerge(Map<String, String> current, Map<String, String> layer) {
		Map<String, String> merged = new LinkedHashMap<>(current);
		merged.putAll(layer);
		return merged;
	}

	private static Map<String, String> eagerImmutableMerge(Map<String, String> current, Map<String, String> layer) {
		Map<String, String> merged = new LinkedHashMap<>(current);
		merged.putAll(layer);
		return Map.copyOf(merged);
	}

	/**
	 * Builds a layer via {@link SortedArrayStringMap#putValue(String, Object)}, the same
	 * "write straight in, no intermediate Map" shape as
	 * {@link #keyValuesLayer(int, int)}.
	 */
	private static SortedArrayStringMap sortedArrayStringMapLayer(int layerIndex, int keysPerLayer) {
		var layer = new SortedArrayStringMap(keysPerLayer);
		for (int k = 0; k < keysPerLayer; k++) {
			layer.putValue("k" + layerIndex + "-" + k, "v" + layerIndex + "-" + k);
		}
		return layer;
	}

	/**
	 * {@link SortedArrayStringMap}'s own copy constructor bulk-copies via
	 * {@code System.arraycopy} when the source is itself a {@link SortedArrayStringMap}
	 * (see {@code initFrom0}), the same trick
	 * {@link KeyValues#merge(KeyValues, KeyValues)} uses for its low side -
	 * {@link SortedArrayStringMap#putAll(org.apache.logging.log4j.util.ReadOnlyStringMap)}
	 * then merges the new layer in with a binary search plus an array-shifting insert per
	 * new key, to keep the result sorted.
	 */
	private static SortedArrayStringMap sortedArrayStringMapMerge(SortedArrayStringMap current,
			SortedArrayStringMap layer) {
		SortedArrayStringMap merged = new SortedArrayStringMap(current);
		merged.putAll(layer);
		return merged;
	}

	@Benchmark
	public void keyValuesPushOneMore(Blackhole bh) {
		bh.consume(KeyValues.merge(keyValues, lastKeyValuesLayer));
	}

	@Benchmark
	public void eagerLinkedHashMapPushOneMore(Blackhole bh) {
		bh.consume(eagerLinkedHashMerge(eagerLinkedHashMap, lastLayer));
	}

	@Benchmark
	public void eagerImmutableMapPushOneMore(Blackhole bh) {
		bh.consume(eagerImmutableMerge(eagerImmutableMap, lastLayer));
	}

	@Benchmark
	public void sortedArrayStringMapPushOneMore(Blackhole bh) {
		bh.consume(sortedArrayStringMapMerge(sortedArrayStringMap, lastSortedArrayStringMapLayer));
	}

	@Benchmark
	public void keyValuesLookupFirstInserted(Blackhole bh) {
		bh.consume(keyValues.getValueOrNull(firstInsertedKey));
	}

	@Benchmark
	public void eagerLinkedHashMapLookupFirstInserted(Blackhole bh) {
		bh.consume(eagerLinkedHashMap.get(firstInsertedKey));
	}

	@Benchmark
	public void eagerImmutableMapLookupFirstInserted(Blackhole bh) {
		bh.consume(eagerImmutableMap.get(firstInsertedKey));
	}

	@Benchmark
	public void sortedArrayStringMapLookupFirstInserted(Blackhole bh) {
		bh.consume(sortedArrayStringMap.getValue(firstInsertedKey));
	}

	@Benchmark
	public void keyValuesLookupLastInserted(Blackhole bh) {
		bh.consume(keyValues.getValueOrNull(lastInsertedKey));
	}

	@Benchmark
	public void eagerLinkedHashMapLookupLastInserted(Blackhole bh) {
		bh.consume(eagerLinkedHashMap.get(lastInsertedKey));
	}

	@Benchmark
	public void eagerImmutableMapLookupLastInserted(Blackhole bh) {
		bh.consume(eagerImmutableMap.get(lastInsertedKey));
	}

	@Benchmark
	public void sortedArrayStringMapLookupLastInserted(Blackhole bh) {
		bh.consume(sortedArrayStringMap.getValue(lastInsertedKey));
	}

}
