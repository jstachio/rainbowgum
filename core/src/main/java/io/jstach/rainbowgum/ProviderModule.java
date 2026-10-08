package io.jstach.rainbowgum;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

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

	FILE_OUTPUT(ComponentType.OUTPUT, "file", "io.jstach.rainbowgum.file", "io.jstach.rainbowgum:rainbowgum-file",
			"file"),
	ROLLING_OUTPUT(ComponentType.OUTPUT, "rolling", "io.jstach.rainbowgum.file", "io.jstach.rainbowgum:rainbowgum-file",
			"rolling"),
	GELF_ENCODER(ComponentType.ENCODER, "gelf", "io.jstach.rainbowgum.json", "io.jstach.rainbowgum:rainbowgum-json",
			"gelf"),
	ECS_ENCODER(ComponentType.ENCODER, "ecs", "io.jstach.rainbowgum.json", "io.jstach.rainbowgum:rainbowgum-json",
			"ecs"),
	LOGSTASH_ENCODER(ComponentType.ENCODER, "logstash", "io.jstach.rainbowgum.json",
			"io.jstach.rainbowgum:rainbowgum-json", "logstash"),
	LOGBACK_JSON_ENCODER(ComponentType.ENCODER, "logback", "io.jstach.rainbowgum.json",
			"io.jstach.rainbowgum:rainbowgum-json", "logback_json"),
	PATTERN_ENCODER(ComponentType.ENCODER, "pattern", "io.jstach.rainbowgum.pattern",
			"io.jstach.rainbowgum:rainbowgum-pattern", "pattern"),
	JFR_OUTPUT(ComponentType.OUTPUT, "jfr", "io.jstach.rainbowgum.jfr", "io.jstach.rainbowgum:rainbowgum-jfr", "jfr"),
	OTLP_OUTPUT(ComponentType.OUTPUT, "otlp", "io.jstach.rainbowgum.otlp", "io.jstach.rainbowgum:rainbowgum-otlp",
			"otlp_output"),
	OTLP_ENCODER(ComponentType.ENCODER, "otlp", "io.jstach.rainbowgum.otlp", "io.jstach.rainbowgum:rainbowgum-otlp",
			"otlp_encoder"),
	// No overview section of its own, so links to the module page instead.
	DISRUPTOR_PUBLISHER(ComponentType.PUBLISHER, "disruptor", "io.jstach.rainbowgum.disruptor",
			"io.jstach.rainbowgum:rainbowgum-disruptor", null);

	private final ComponentType componentType;

	private final String scheme;

	private final String moduleName;

	private final String mavenGroupArtifact;

	private final @Nullable String overviewAnchor;

	private ProviderModule(ComponentType componentType, String scheme, String moduleName, String mavenGroupArtifact,
			@Nullable String overviewAnchor) {
		this.componentType = componentType;
		this.scheme = scheme;
		this.moduleName = moduleName;
		this.mavenGroupArtifact = mavenGroupArtifact;
		this.overviewAnchor = overviewAnchor;
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
	 * The Maven {@code groupId:artifactId:version} that provides {@link #moduleName()},
	 * using this Rainbow Gum's own {@link RainbowGumVersion#VERSION}.
	 * @return maven GAV.
	 */
	String mavenGav() {
		return mavenGroupArtifact + ":" + RainbowGumVersion.VERSION;
	}

	/**
	 * The documentation for this scheme at {@link RainbowGumVersion#documentBaseUrl()}:
	 * its section of the overview if it has one, otherwise the javadoc page of
	 * {@link #moduleName()}.
	 * @return doc URL.
	 */
	String docUrl() {
		String base = RainbowGumVersion.documentBaseUrl();
		var anchor = overviewAnchor;
		if (anchor != null) {
			return base + "/index.html#" + anchor;
		}
		return base + "/" + moduleName + "/module-summary.html";
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
