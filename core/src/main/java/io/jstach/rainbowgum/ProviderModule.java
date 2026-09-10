package io.jstach.rainbowgum;

import java.util.Optional;

/**
 * Catalogs the optional modules (not bundled in {@code rainbowgum-core}) that are known
 * to register a particular URI scheme for a particular kind of provider registry
 * (output/publisher/encoder). This is not a service discovery mechanism - it is a fixed,
 * hand maintained list used purely to make {@link LogProviderRef.NotFoundException}
 * messages more actionable: if a scheme is not registered <em>and</em> it happens to be
 * one we know is provided by an optional module, the message can say which dependency to
 * add instead of just "not found".
 */
enum ProviderModule {

	FILE_OUTPUT(ComponentType.OUTPUT, "file", "io.jstach.rainbowgum.file", "io.jstach.rainbowgum:rainbowgum-file"),
	ROLLING_OUTPUT(ComponentType.OUTPUT, "rolling", "io.jstach.rainbowgum.file",
			"io.jstach.rainbowgum:rainbowgum-file"),
	GELF_ENCODER(ComponentType.ENCODER, "gelf", "io.jstach.rainbowgum.json", "io.jstach.rainbowgum:rainbowgum-json"),
	ECS_ENCODER(ComponentType.ENCODER, "ecs", "io.jstach.rainbowgum.json", "io.jstach.rainbowgum:rainbowgum-json"),
	LOGSTASH_ENCODER(ComponentType.ENCODER, "logstash", "io.jstach.rainbowgum.json",
			"io.jstach.rainbowgum:rainbowgum-json"),
	LOGBACK_JSON_ENCODER(ComponentType.ENCODER, "logback", "io.jstach.rainbowgum.json",
			"io.jstach.rainbowgum:rainbowgum-json"),
	PATTERN_ENCODER(ComponentType.ENCODER, "pattern", "io.jstach.rainbowgum.pattern",
			"io.jstach.rainbowgum:rainbowgum-pattern"),
	DISRUPTOR_PUBLISHER(ComponentType.PUBLISHER, "disruptor", "io.jstach.rainbowgum.disruptor",
			"io.jstach.rainbowgum:rainbowgum-disruptor");

	private final ComponentType componentType;

	private final String scheme;

	private final String moduleName;

	private final String mavenGav;

	private ProviderModule(ComponentType componentType, String scheme, String moduleName, String mavenGav) {
		this.componentType = componentType;
		this.scheme = scheme;
		this.moduleName = moduleName;
		this.mavenGav = mavenGav;
	}

	/**
	 * The kind of provider registry this scheme is registered with.
	 * @return component type.
	 */
	ComponentType componentType() {
		return componentType;
	}

	/**
	 * The URI scheme the module registers.
	 * @return scheme.
	 */
	String scheme() {
		return scheme;
	}

	/**
	 * The Java module ({@code module-info.java}) name the scheme is registered from.
	 * @return module name.
	 */
	String moduleName() {
		return moduleName;
	}

	/**
	 * The Maven {@code groupId:artifactId} that provides {@link #moduleName()}.
	 * @return maven GAV (without version).
	 */
	String mavenGav() {
		return mavenGav;
	}

	/**
	 * Finds a known module by component type and scheme.
	 * @param componentType kind of provider registry.
	 * @param scheme URI scheme that was not found.
	 * @return module if the combination of type and scheme is known, empty otherwise.
	 */
	static Optional<ProviderModule> find(ComponentType componentType, String scheme) {
		for (var m : values()) {
			if (m.componentType == componentType && m.scheme.equals(scheme)) {
				return Optional.of(m);
			}
		}
		return Optional.empty();
	}

	/**
	 * The different kinds of provider registries that resolve components by URI scheme.
	 */
	enum ComponentType {

		/**
		 * {@link LogOutputRegistry}.
		 */
		OUTPUT("output"),
		/**
		 * {@link LogPublisherRegistry}.
		 */
		PUBLISHER("publisher"),
		/**
		 * {@link LogEncoderRegistry}.
		 */
		ENCODER("encoder");

		private final String label;

		private ComponentType(String label) {
			this.label = label;
		}

		/**
		 * The label used in {@link LogProviderRef.NotFoundException} messages, e.g.
		 * "output"/"publisher"/"encoder".
		 * @return label.
		 */
		String label() {
			return label;
		}

	}

}
