package io.jstach.rainbowgum.apt;

import static java.util.Objects.requireNonNull;

import java.beans.Introspector;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.apt.prism.ConfigObjectPrism;
import io.jstach.svc.ServiceProvider;

@SupportedAnnotationTypes({ ConfigObjectPrism.PRISM_ANNOTATION_TYPE })
@ServiceProvider(value = Processor.class)
public class ConfigProcessor extends AbstractProcessor {

	// Set<ConfigBeanDefinitionModel> configBeanDefinitions =
	// Collections.newSetFromMap(new ConcurrentHashMap<>());

	private static final String CONFIG_BEAN_CLASS = ConfigObjectPrism.PRISM_ANNOTATION_TYPE;

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		final Helper h = new Helper(requireNonNull(processingEnv));
		if (!roundEnv.processingOver()) {
			TypeElement configBeanElement = processingEnv.getElementUtils().getTypeElement(CONFIG_BEAN_CLASS);
			if (configBeanElement == null) {
				processingEnv.getMessager().printMessage(Kind.ERROR, "Config library not in classpath");
				throw new NullPointerException("ConfigBuilder element missing");
			}
			for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(configBeanElement)) {
				if (annotatedElement.getKind() != ElementKind.METHOD) {
					processingEnv.getMessager()
						.printMessage(Kind.ERROR, "@" + CONFIG_BEAN_CLASS + " should be a method", annotatedElement);
					continue;
				}
				ExecutableElement ee = (ExecutableElement) annotatedElement;
				ConfigObjectPrism prism = ConfigObjectPrism.getInstanceOn(annotatedElement);
				model(h, prism, ee);
			}
		}
		return false;
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Nullable
	private BuilderModel model(Helper h, ConfigObjectPrism prism, ExecutableElement ee) {

		TypeElement enclosingType = (TypeElement) ee.getEnclosingElement();
		String builderName = prism.name();
		String propertyPrefix = prism.prefix();
		String packageName = h.getPackageString(enclosingType);
		String targetType = h.getFullyQualifiedClassName(ee.getReturnType());
		String factoryMethod = enclosingType + "." + ee.getSimpleName();
		List<BuilderModel.PropertyModel> properties = new ArrayList<>();

		List<? extends VariableElement> parameters = ee.getParameters();
		for (var p : parameters) {
			String name = p.getSimpleName().toString();
			String type = h.getFullyQualifiedClassName(p.asType());
			String typeWithAnnotation = ToStringTypeVisitor.toCodeSafeString(p.asType());
			String defaultValue = "null";
			boolean required = !h.isNullable(p.asType());
			var prop = new BuilderModel.PropertyModel(name, type, typeWithAnnotation, defaultValue, required);
			properties.add(prop);
		}

		var m = new BuilderModel(builderName, propertyPrefix, packageName, targetType, factoryMethod, properties);
		String java = BuilderModelRenderer.of().execute(m);
		try {
			processingEnv.getMessager()
				.printMessage(Kind.NOTE, "Generating ConfigBuilder. name: " + m.packageName() + "." + m.builderName());
			JavaFileObject file = h.createSourceFile(ee, m.packageName(), m.builderName());
			try (PrintWriter pw = new PrintWriter(file.openWriter())) {
				pw.print(java);
			}
		}
		catch (IOException e1) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to create config builder", ee);
			return null;
		}
		catch (Exception e1) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to create config builder", ee);
			return null;
		}
		return m;
	}

	private static Class<?> primitiveToClass(TypeKind k) {
		if (!k.isPrimitive())
			throw new IllegalArgumentException("k is not primitive: " + k);
		switch (k) {
			case BOOLEAN:
				return Boolean.class;
			case BYTE:
				return Byte.class;
			case CHAR:
				return Character.class;
			case DOUBLE:
				return Double.class;
			case FLOAT:
				return Float.class;
			case INT:
				return Integer.class;
			case LONG:
				return Long.class;
			case SHORT:
				return Short.class;
			default:
				throw new IllegalArgumentException("k is not a primitive" + k);

		}
	}

	private static String propertyNameFromMethodName(String propertyName) {
		int length = propertyName.length();
		if (length > 3 && propertyName.startsWith("get")) {
			return requireNonNull(Introspector.decapitalize(propertyName.substring(3)));
		}
		else if (length > 2 && propertyName.startsWith("is")) {
			return requireNonNull(Introspector.decapitalize(propertyName.substring(2)));
		}
		return propertyName;
	}

	static class Helper {

		private final Types types;

		private final Elements elements;

		private final Filer filer;

		private final Messager messager;

		public Helper(ProcessingEnvironment pe) {
			this(pe.getTypeUtils(), pe.getElementUtils(), pe.getFiler(), pe.getMessager());
		}

		public Helper(Types types, Elements elements, Filer filer, Messager messager) {
			super();
			this.types = types;
			this.elements = elements;
			this.filer = filer;
			this.messager = messager;
		}

		public String getPackageString(TypeElement te) {
			return elements.getPackageOf(te).getQualifiedName().toString();
		}

		public JavaFileObject createSourceFile(final Element baseElement, final String packageName,
				final String className) throws Exception {

			final String suffix = packageName.isEmpty() ? "" : packageName + ".";
			return filer.createSourceFile(suffix + className, baseElement);
		}

		public FileObject getResourceFile(final String file) throws IOException {
			return filer.getResource(StandardLocation.CLASS_OUTPUT, "", file);
		}

		public FileObject createResourceFile(final String file) throws IOException {
			return filer.createResource(StandardLocation.CLASS_OUTPUT, "", file);
		}

		public Stream<ExecutableElement> getAllMethods(final TypeElement te) {
			List<TypeElement> ancestors = ancestors(te);
			Collections.reverse(ancestors);
			ElementScanner8<@Nullable Void, Collection<@NonNull ExecutableElement>> scanner = new ElementScanner8<@Nullable Void, Collection<@NonNull ExecutableElement>>() {
				@Override
				public @Nullable Void visitExecutable(ExecutableElement e, Collection<ExecutableElement> p) {
					p.add(e);
					return null;
				}
			};
			List<ExecutableElement> es = new ArrayList<>();
			scanner.scan(ancestors, es);
			return es.stream();

		}

		public static boolean isPublicVirtual(Set<Modifier> modifiers) {
			return modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)
					&& !modifiers.contains(Modifier.NATIVE) && !modifiers.contains(Modifier.ABSTRACT);
		}

		public static boolean isPublicAbstract(Set<Modifier> modifiers) {
			return modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)
					&& !modifiers.contains(Modifier.NATIVE) && modifiers.contains(Modifier.ABSTRACT);
		}

		public List<TypeElement> ancestors(@Nullable final TypeElement e) {
			List<TypeElement> list = new ArrayList<>();
			@Nullable
			TypeElement c = e;
			while (c != null) {
				list.add(c);
				TypeMirror tm = c.getSuperclass();
				TypeElement t = (TypeElement) types.asElement(tm);
				if (t == null)
					return list;
				c = t;
			}
			return list;
		}

		public <E extends Element> Predicate<E> reportPredicate(final Kind kind, final String message,
				final Predicate<E> p) {
			return new Predicate<E>() {
				@Override
				public boolean test(E e) {
					boolean b = p.test(e);
					if (!b)
						messager.printMessage(kind, message, e);
					return b;
				}
			};
		}

		public boolean isListType(TypeMirror tm) {
			DeclaredType dt = (DeclaredType) tm;
			return isListType((TypeElement) dt.asElement());
		}

		public boolean isListType(TypeElement te) {
			return te.getQualifiedName().toString().equals("java.util.List");
		}

		public String getBinaryName(TypeElement element) {
			return getBinaryNameImpl(element, element.getSimpleName().toString());
		}

		public String getBinaryNameImpl(TypeElement element, String className) {
			Element enclosingElement = element.getEnclosingElement();

			if (enclosingElement instanceof PackageElement) {
				PackageElement pkg = (PackageElement) enclosingElement;
				if (pkg.isUnnamed()) {
					return className;
				}
				return pkg.getQualifiedName() + "." + className;
			}

			TypeElement typeElement = (TypeElement) enclosingElement;
			return getBinaryNameImpl(typeElement, typeElement.getSimpleName() + "$" + className);
		}

		public String getFullyQualifiedClassName(TypeMirror t) {

			if (t.getKind() == TypeKind.DECLARED) {
				TypeElement te = requireNonNull((TypeElement) types.asElement(t));
				return te.getQualifiedName().toString();
			}
			else {
				return t.toString();
			}

		}

		public String getFullyQualifiedClassNameWithTypeAnnotations(TypeMirror t) {

			if (t.getKind() == TypeKind.DECLARED) {
				TypeElement te = requireNonNull((TypeElement) types.asElement(t));
				String ats = t.getAnnotationMirrors()
					.stream()
					.map(am -> typeUseAnnotationFQN(am))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(s -> "@" + s)
					.collect(Collectors.joining(" "));
				String packageLikeName;
				Element ee = te.getEnclosingElement();
				if (ee instanceof TypeElement) {
					packageLikeName = ((TypeElement) ee).getQualifiedName().toString();
				}
				else if (ee instanceof PackageElement) {
					packageLikeName = ((PackageElement) ee).getQualifiedName().toString();
				}
				else {
					packageLikeName = "";
				}
				packageLikeName = packageLikeName.isEmpty() ? "" : packageLikeName + ".";
				ats = ats.isEmpty() ? "" : ats + " ";
				return packageLikeName + ats + te.getSimpleName();
			}
			else {
				return t.toString();
			}

		}

		public boolean isNullable(TypeMirror t) {
			if (t.getKind() != TypeKind.DECLARED) {
				return false;
			}
			return t.getAnnotationMirrors()
				.stream()
				.flatMap(am -> typeUseAnnotationFQN(am).stream())
				.filter(s -> s.endsWith(".Nullable"))
				.findAny()
				.isPresent();
		}

		Optional<String> typeUseAnnotationFQN(
				// TypeElement te,
				@Nullable AnnotationMirror am) {
			if (am == null)
				return Optional.empty();
			DeclaredType dt = am.getAnnotationType();
			return Optional.of(getFullyQualifiedClassName(dt));
		}

	}

}
