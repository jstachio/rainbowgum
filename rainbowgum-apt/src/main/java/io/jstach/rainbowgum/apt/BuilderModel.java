package io.jstach.rainbowgum.apt;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheLambda;
import io.jstach.jstache.JStacheType;

@JStacheConfig(type = JStacheType.STACHE)
@JStache(path = "io/jstach/rainbowgum/apt/ConfigBuilder.java")
record BuilderModel( //
		String builderName, //
		String propertyPrefix, //
		String packageName, //
		String targetType, //
		String factoryMethod, //
		String description, //
		List<PropertyModel> properties, List<String> exceptions) {

	public String nullableAnnotation() {
		return "org.eclipse.jdt.annotation.Nullable";
	}

	public String LB() {
		return "{";
	}

	public String RB() {
		return "}";
	}

	public List<PropertyModel> normalProperties() {
		return properties.stream().filter(p -> p.kind == PropertyKind.NORMAL).toList();
	}

	public List<PropertyModel> prefixParameters() {
		return properties.stream().filter(p -> p.kind == PropertyKind.NAME_PARAMETER).toList();
	}

	public List<PropertyModel> passThroughParameters() {
		return properties.stream().filter(p -> p.kind == PropertyKind.PASSTHROUGH).toList();
	}

	public List<String> descriptionLines() {
		return description.lines().map(String::trim).toList();
	}

	/*
	 * ConfigBuilder.java's build() wraps the factoryMethod call in a plain try/catch,
	 * catching only IllegalArgumentException/LogProperty.PropertyMissingException (a
	 * curated allowlist of what a factory method's own defensive checks or
	 * LogProperty.require(...) realistically throw - not a blanket RuntimeException, so
	 * an unrelated bug is not silently mislabeled "Validation failed") and rethrowing via
	 * LogProperty.ValidationException.of(...) - a public static factory next to
	 * ValidationException's own existing validate(Class, List) one, since
	 * ValidationException's constructor itself is package-private.
	 * PropertyMissingException's own constructor is likewise package-private, but the
	 * type itself is not - a member type declared in a public interface (LogProperty is
	 * one) is implicitly public per JLS 9.5 regardless of written modifiers, so generated
	 * code in any package can name it in a catch clause directly. That catch clause is
	 * completely orthogonal to checked exceptions - it never touches them - so
	 * exceptions/throwsList() below (genuinely read from the factory method's own throws
	 * clause via ee.getThrownTypes() in ConfigProcessor.model(...)) keeps working exactly
	 * as it already did, unaffected by any of this.
	 */
	public String throwsList() {
		if (exceptions.isEmpty()) {
			return "";
		}
		return " throws " + exceptions.stream().collect(Collectors.joining(", "));
	}

	// https://github.com/jstachio/jstachio/issues/325
	@JStacheLambda(
			template = "{{#checkForNull}}io.jstach.rainbowgum.LogProperty.require({{propertyVar}}, {{> @section}}){{/checkForNull}}{{^checkForNull}}{{> @section}}{{/checkForNull}}")
	public String validate(PropertyModel pm) {
		return "";
	}

	record Converter(String methodName) {
	}

	record PropertyModel(PropertyKind kind, //
			String name, //
			String type, //
			String typeWithAnnotation, //
			String typeWithNoAnnotation, //
			String fieldType, //
			ClassRef classRef, //
			String defaultValue, //
			boolean required, //
			String javadoc, //
			@Nullable Converter converter) {

		private static final String INTEGER_TYPE = "java.lang.Integer";

		private static final String URI_TYPE = "java.net.URI";

		private static final String STRING_TYPE = "java.lang.String";

		private static final String BOOLEAN_TYPE = "java.lang.Boolean";

		private static final String MAP_TYPE = "java.util.Map";

		private static final String LIST_TYPE = "java.util.List";

		public String propertyVar() {
			return "property_" + name;
		}

		public String propertyLiteral() {
			return "PROPERTY_" + name;
		}

		// public String fieldType() {
		// if (defaultValue.equals("null") && !typeWithAnnotation.contains("Nullable")) {
		// return classRef.getPackageName() + ".@org.eclipse.jdt.annotation.Nullable " +
		// classRef.getSimpleName();
		// }
		// return typeWithAnnotation;
		// }

		/**
		 * The {@link io.jstach.rainbowgum.LogProperty} accessor method to call to get
		 * this property's raw (pre-converter)
		 * {@link io.jstach.rainbowgum.LogProperty.Result}. A property with a custom
		 * {@link #converter} always reads as a plain string - every
		 * {@code @ConvertParameter} method takes a {@code String} - regardless of its
		 * declared {@link #type}.
		 * @return LogProperty method name, no parens, e.g. "ofInt".
		 */
		public String baseAccessor() {
			if (converter != null) {
				return "ofString";
			}
			return switch (type) {
				case INTEGER_TYPE -> "ofInt";
				case STRING_TYPE -> "ofString";
				case URI_TYPE -> "ofURI";
				case BOOLEAN_TYPE -> "ofBoolean";
				case MAP_TYPE -> "ofMap";
				case LIST_TYPE -> "ofList";
				default -> throw new IllegalStateException(type + " is not supported");
			};
		}

		public boolean hasConverter() {
			return converter != null;
		}

		public String converterMethodName() {
			var c = converter;
			if (c == null) {
				throw new IllegalStateException("no converter");
			}
			return c.methodName;
		}

		public String typeDescription() {
			return switch (type) {
				case INTEGER_TYPE -> "Integer";
				case STRING_TYPE -> "String";
				case URI_TYPE -> "URI";
				case BOOLEAN_TYPE -> "Boolean";
				case MAP_TYPE -> "{@link LogProperties#mapOrNull(String) Map&lt;String,String&gt;}";
				case LIST_TYPE -> "{@link LogProperties#listOrNull(String) List&lt;String&gt;}";
				default -> "String (converted)";
			};
		}

		public boolean checkForNull() {
			if (isPrefixParameter()) {
				return false;
			}
			return required;
		}

		boolean isLiteralType() {
			return switch (type) {
				case INTEGER_TYPE, STRING_TYPE, BOOLEAN_TYPE -> true;
				default -> false;
			};
		}

		public String valueMethod() {
			return required ? "value" : "valueOrNull";
		}

		public String validateMethod() {
			return required ? "validate" : "validateIfError";
		}

		public boolean isNormal() {
			return kind == PropertyKind.NORMAL;
		}

		public boolean isPrefixParameter() {
			return kind == PropertyKind.NAME_PARAMETER;
		}

		public boolean isPassThrough() {
			return kind == PropertyKind.PASSTHROUGH;
		}

		public String defaultValueDoc() {
			if (defaultValue.equals("null")) {
				return "<code>null</code>";
			}
			String link = linkStatic(defaultValue);
			if (isLiteralType()) {
				return "{@value " + link + " }";
			}
			return "{@link " + link + " }";
		}

		private static String linkStatic(String constant) {
			int index = constant.lastIndexOf(".");
			if (index < 0) {
				return constant;
			}
			StringBuilder sb = new StringBuilder(constant);
			sb.setCharAt(index, '#');
			return sb.toString();
		}
	}

	enum PropertyKind {

		NORMAL, NAME_PARAMETER, PASSTHROUGH

	}

}
