package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter.KeyValuesCarrier;

/*
 * Prototype/experimental - see ScopedKeyValuesProviderImpl#push. A pure data carrier
 * attached via addSuppressed to whatever exception escapes a push(...) boundary: no
 * message, no cause, and both writableStackTrace and enableSuppression are disabled
 * (nothing to walk/capture for a marker that will never itself be thrown), so the only
 * real cost is the KeyValues reference it holds.
 */
final class ScopedContextMarker extends Throwable implements KeyValuesCarrier {

	private static final long serialVersionUID = 1L;

	private final transient KeyValues keyValues;

	ScopedContextMarker(KeyValues keyValues) {
		super(null, null, false, false);
		this.keyValues = keyValues;
	}

	@Override
	public KeyValues keyValues() {
		return keyValues;
	}

}
