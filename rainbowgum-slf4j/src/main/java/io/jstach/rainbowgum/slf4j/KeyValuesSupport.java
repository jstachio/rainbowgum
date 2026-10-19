package io.jstach.rainbowgum.slf4j;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.JsonSerializer;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

/*
 * The contributor handlers are built with also carries the config's JSON serializer, so
 * the serializer follows the bound RainbowGum the same way contributors do.
 */
interface KeyValuesSupport extends KeyValuesContributor {

	@Nullable JsonSerializer jsonSerializer();

}
