package io.jstach.rainbowgum;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogProperty.Validator;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Test-only encoder that is never actually reached (every {@link ConfigFailureTest} case
 * fails before {@code build(LogConfig)} succeeds) - it exists purely so
 * {@link ConfigFailureTest} can exercise a hand-rolled builder (shaped like what
 * rainbowgum-apt generates) with a spread of property types: String/Integer/URI/List/Map.
 */
final class FakeEncoderConfigurator implements Configurator {

	static final String FAKE_SCHEME = "fake";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(FAKE_SCHEME, new FakeEncoderProvider());
		return true;
	}

	private static class FakeEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, c) -> {
				var b = new FakeEncoderBuilder(name);
				b.fromProperties(c.properties(), ref);
				return b.build();
			};
		}

	}

}

/*
 * Hand-rolled analog of an rainbowgum-apt generated builder (see GelfEncoderBuilder) -
 * kept by hand here rather than annotation-processed since this test module has no need
 * to wire up apt just for one fake component.
 */
final class FakeEncoderBuilder implements LogBuilder<FakeEncoderBuilder, LogEncoder> {

	static final String PROPERTY_PREFIX = "logging.encoder.{name}.";

	static final String PROPERTY_host = PROPERTY_PREFIX + "host";

	static final String PROPERTY_label = PROPERTY_PREFIX + "label";

	static final String PROPERTY_port = PROPERTY_PREFIX + "port";

	static final String PROPERTY_endpoint = PROPERTY_PREFIX + "endpoint";

	static final String PROPERTY_tags = PROPERTY_PREFIX + "tags";

	static final String PROPERTY_headers = PROPERTY_PREFIX + "headers";

	private final String propertyPrefix;

	private final String property_host;

	private final String property_label;

	private final String property_port;

	private final String property_endpoint;

	private final String property_tags;

	private final String property_headers;

	private final String name;

	private @Nullable String host = null;

	private @Nullable String label = null;

	private @Nullable Integer port = null;

	private @Nullable URI endpoint = null;

	private @Nullable List<String> tags = null;

	private @Nullable Map<String, String> headers = null;

	FakeEncoderBuilder(String name) {
		Map<String, String> prefixParameters = Map.of("name", name);
		this.propertyPrefix = LogProperties.interpolateKey(PROPERTY_PREFIX, prefixParameters);
		this.name = name;
		this.property_host = LogProperties.interpolateKey(PROPERTY_host, prefixParameters);
		this.property_label = LogProperties.interpolateKey(PROPERTY_label, prefixParameters);
		this.property_port = LogProperties.interpolateKey(PROPERTY_port, prefixParameters);
		this.property_endpoint = LogProperties.interpolateKey(PROPERTY_endpoint, prefixParameters);
		this.property_tags = LogProperties.interpolateKey(PROPERTY_tags, prefixParameters);
		this.property_headers = LogProperties.interpolateKey(PROPERTY_headers, prefixParameters);
	}

	FakeEncoderBuilder host(String host) {
		this.host = host;
		return this;
	}

	FakeEncoderBuilder label(@Nullable String label) {
		this.label = label;
		return this;
	}

	FakeEncoderBuilder port(@Nullable Integer port) {
		this.port = port;
		return this;
	}

	FakeEncoderBuilder endpoint(@Nullable URI endpoint) {
		this.endpoint = endpoint;
		return this;
	}

	FakeEncoderBuilder tags(@Nullable List<String> tags) {
		this.tags = tags;
		return this;
	}

	FakeEncoderBuilder headers(@Nullable Map<String, String> headers) {
		this.headers = headers;
		return this;
	}

	LogEncoder build() {
		String h = LogProperty.require(property_host, this.host);
		LogFormatter.EventFormatter formatter = (out, event) -> {
			out.append(h).append(':');
			event.formattedMessage(out);
		};
		return LogEncoder.builder(formatter).build().provide(name, LogConfig.builder().build());
	}

	@Override
	public FakeEncoderBuilder fromProperties(LogProperties properties) {
		var __v = Validator.of(this.getClass());
		var _host = properties.forKey(property_host).ofString().or(this.host).validate(__v);
		/*
		 * Deliberately Result.map() here, not LogProperty.ofXxx()/Result.convert() -
		 * exercises the plain end-of-fluent-chain mapping path (Success.map(), which
		 * builds the exact same richError() message convert() does - both read the
		 * broader/aggregate LogProperties for the "Tried:" line off the result's own
		 * PropertySuccess.topProperties(), not a parameter either method is given). See
		 * ConfigFailureTest.encoderCustomStringValidationFailure().
		 */
		var _label = properties.forKey(property_label).ofString().map(l -> {
			if (l.equals("bad")) {
				throw new IllegalArgumentException("label must not be 'bad'");
			}
			return l;
		}).or(this.label).validateIfError(__v);
		var _port = properties.forKey(property_port).ofInt().or(this.port).validateIfError(__v);
		var _endpoint = properties.forKey(property_endpoint).ofURI().or(this.endpoint).validateIfError(__v);
		// same Result.map() (not convert()) path as _label above, but on a List.
		var _tags = properties.forKey(property_tags).ofList().map(list -> {
			if (list.contains("bad")) {
				throw new IllegalArgumentException("tags must not contain 'bad'");
			}
			return list;
		}).or(this.tags).validateIfError(__v);
		// same Result.map() (not convert()) path as _label above, but on a Map.
		var _headers = properties.forKey(property_headers).ofMap().map(map -> {
			if (map.containsKey("bad")) {
				throw new IllegalArgumentException("headers must not contain key 'bad'");
			}
			return map;
		}).or(this.headers).validateIfError(__v);
		__v.validate();
		this.host = _host.value();
		this.label = _label.valueOrNull();
		this.port = _port.valueOrNull();
		this.endpoint = _endpoint.valueOrNull();
		this.tags = _tags.valueOrNull();
		this.headers = _headers.valueOrNull();
		return this;
	}

	@Override
	public String propertyPrefix() {
		return this.propertyPrefix;
	}

}
