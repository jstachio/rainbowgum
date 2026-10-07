package io.jstach.rainbowgum.apt;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import org.jspecify.annotations.Nullable;

import io.jstach.svc.ServiceProvider;

/**
 * Checks <code>io.jstach.rainbowgum.annotation.EnumAlias</code> and records every alias
 * in the class output resource {@value #RESOURCE}, which source retention otherwise
 * leaves nowhere for tests to find. Each line is the enum's binary name, the constant,
 * and one alias, separated by tabs, sorted.
 *
 * @apiNote The generated resource is not public API: its name, location, and format may
 * change in any release and must not be relied on.
 */
@SupportedAnnotationTypes(EnumAliasProcessor.ENUM_ALIAS)
@ServiceProvider(value = Processor.class)
public class EnumAliasProcessor extends AbstractProcessor {

	static final String ENUM_ALIAS = "io.jstach.rainbowgum.annotation.EnumAlias";

	/**
	 * The resource listing the aliases of the compiled module. Not public API, see the
	 * class description.
	 */
	public static final String RESOURCE = "META-INF/rainbowgum/enum-aliases.txt";

	/*
	 * enum binary name -> constant -> aliases, kept across rounds and written once.
	 */
	private final Map<String, Map<String, List<String>>> aliases = new TreeMap<>();

	private final List<Element> origins = new ArrayList<>();

	/**
	 * For service loader.
	 */
	public EnumAliasProcessor() {
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			write();
			return false;
		}
		TypeElement annotation = processingEnv.getElementUtils().getTypeElement(ENUM_ALIAS);
		if (annotation == null) {
			return false;
		}
		for (Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
			record(e, annotation);
		}
		return false;
	}

	private void record(Element constant, TypeElement annotation) {
		var messager = processingEnv.getMessager();
		if (constant.getKind() != ElementKind.ENUM_CONSTANT) {
			messager.printMessage(Diagnostic.Kind.ERROR, "@EnumAlias is only for enum constants", constant);
			return;
		}
		var enumType = (TypeElement) constant.getEnclosingElement();
		List<String> values = values(constant, annotation);
		if (values.isEmpty()) {
			messager.printMessage(Diagnostic.Kind.ERROR, "@EnumAlias needs at least one alias", constant);
			return;
		}
		String enumName = processingEnv.getElementUtils().getBinaryName(enumType).toString();
		var constants = aliases.computeIfAbsent(enumName, k -> new LinkedHashMap<>());
		for (String alias : values) {
			String problem = problem(enumType, constants, alias);
			if (problem != null) {
				messager.printMessage(Diagnostic.Kind.ERROR, problem, constant);
				return;
			}
		}
		constants.put(constant.getSimpleName().toString(), values);
		origins.add(constant);
	}

	/*
	 * An alias must be usable as a property value and must select only one constant, case
	 * insensitive like the property values themselves.
	 */
	private static @Nullable String problem(TypeElement enumType, Map<String, List<String>> constants, String alias) {
		if (alias.isBlank() || !alias.strip().equals(alias)) {
			return "@EnumAlias alias '" + alias + "' is blank or has surrounding whitespace";
		}
		for (Element e : enumType.getEnclosedElements()) {
			if (e.getKind() == ElementKind.ENUM_CONSTANT && e.getSimpleName().toString().equalsIgnoreCase(alias)) {
				return "@EnumAlias alias '" + alias + "' is already the name of constant " + e.getSimpleName();
			}
		}
		String lower = alias.toLowerCase(Locale.ROOT);
		for (var entry : constants.entrySet()) {
			for (String other : entry.getValue()) {
				if (other.toLowerCase(Locale.ROOT).equals(lower)) {
					return "@EnumAlias alias '" + alias + "' is already an alias of constant " + entry.getKey();
				}
			}
		}
		return null;
	}

	private static List<String> values(Element constant, TypeElement annotation) {
		var result = new ArrayList<String>();
		for (AnnotationMirror mirror : constant.getAnnotationMirrors()) {
			if (!mirror.getAnnotationType().asElement().equals(annotation)) {
				continue;
			}
			for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues()
				.entrySet()) {
				if (!entry.getKey().getSimpleName().contentEquals("value")) {
					continue;
				}
				Object value = entry.getValue().getValue();
				if (value instanceof List<?> list) {
					for (Object item : list) {
						if (item instanceof AnnotationValue av && av.getValue() instanceof String s) {
							result.add(s);
						}
					}
				}
				else if (value instanceof String s) {
					result.add(s);
				}
			}
		}
		return result;
	}

	private void write() {
		if (aliases.isEmpty()) {
			return;
		}
		try {
			var file = processingEnv.getFiler()
				.createResource(StandardLocation.CLASS_OUTPUT, "", RESOURCE, origins.toArray(new Element[0]));
			try (Writer w = new java.io.OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8)) {
				for (var e : aliases.entrySet()) {
					for (var c : new TreeMap<>(e.getValue()).entrySet()) {
						for (String alias : c.getValue()) {
							w.write(e.getKey() + "\t" + c.getKey() + "\t" + alias + "\n");
						}
					}
				}
			}
		}
		catch (IOException e) {
			processingEnv.getMessager()
				.printMessage(Diagnostic.Kind.ERROR, "Could not write " + RESOURCE + ": " + e.getMessage());
		}
	}

}
