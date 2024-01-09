package io.jstach.rainbowgum.apt;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import io.jstach.rainbowgum.apt.BuilderModel.PropertyModel;

@JStacheConfig(type = JStacheType.STACHE)
@JStache(path = "io/jstach/rainbowgum/apt/ConfigBuilder.java")
record BuilderModel( //
		String builderName, //
		String propertyPrefix, //
		String packageName, //
		String targetType, //
		String factoryMethod, //
		String description, //
		List<PropertyModel> properties) {

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

	record Converter(String methodName) {
	}

	record PropertyModel(PropertyKind kind, //
			String name, //
			String type, //
			String typeWithAnnotation, //
			String defaultValue, //
			boolean required, //
			String javadoc, //
			@Nullable Converter converter) {

		private static final String INTEGER_TYPE = "java.lang.Integer";

		private static final String URI_TYPE = "java.net.URI";

		private static final String STRING_TYPE = "java.lang.String";

		private static final String BOOLEAN_TYPE = "java.lang.Boolean";

		public String propertyVar() {
			return "property_" + name;
		}

		public String propertyLiteral() {
			return "PROPERTY_" + name;
		}

		public String convertMethod() {
			if (converter != null) {
				return ".map(_v -> " + converter.methodName + "(_v))";
			}
			return switch (type) {
				case INTEGER_TYPE -> ".toInt()";
				case STRING_TYPE -> "";
				case URI_TYPE -> ".toURI()";
				case BOOLEAN_TYPE -> ".toBoolean()";
				default -> throw new IllegalStateException(type + " is not supported");
			};
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

		public boolean isNormal() {
			return kind == PropertyKind.NORMAL;
		}

		public boolean isPrefixParameter() {
			return kind == PropertyKind.NAME_PARAMETER;
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

		NORMAL, NAME_PARAMETER

	}

}
