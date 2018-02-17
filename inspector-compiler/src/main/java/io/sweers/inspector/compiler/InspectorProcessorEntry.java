package io.sweers.inspector.compiler;

import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import io.sweers.inspector.CompositeValidator;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.SelfValidating;
import io.sweers.inspector.Types;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.ValidationQualifier;
import io.sweers.inspector.Validator;
import io.sweers.inspector.compiler.annotations.GenerateValidator;
import io.sweers.inspector.compiler.plugins.spi.InspectorCompilerContext;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class) public final class InspectorProcessorEntry extends AbstractProcessor {

  public static final String OPTION_PREFER_KOTLIN = "inspector.preferKotlin";

  // Depending on how this InspectorProcessor was constructed, we might already have a list of
  // extensions when init() is run, or, if `extensions` is null, we have a ClassLoader that will be
  // used to get the list using the ServiceLoader API.
  private Set<InspectorExtension> extensions;
  @Nullable private final ClassLoader loaderForExtensions;
  private Messager messager;
  private Elements elements;
  private javax.lang.model.util.Types typeUtils;
  private InspectorProcessor processor;
  private boolean preferKotlin;

  public InspectorProcessorEntry() {
    this(InspectorProcessorEntry.class.getClassLoader());
  }

  @VisibleForTesting InspectorProcessorEntry(ClassLoader loaderForExtensions) {
    this.extensions = null;
    this.loaderForExtensions = loaderForExtensions;
  }

  @VisibleForTesting public InspectorProcessorEntry(Iterable<? extends InspectorExtension> extensions) {
    this.extensions = ImmutableSet.copyOf(extensions);
    this.loaderForExtensions = null;
  }

  @Override public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    elements = processingEnv.getElementUtils();
    typeUtils = processingEnv.getTypeUtils();

    try {
      extensions =
          ImmutableSet.copyOf(ServiceLoader.load(InspectorExtension.class, loaderForExtensions));
      // ServiceLoader.load returns a lazily-evaluated Iterable, so evaluate it eagerly now
      // to discover any exceptions.
    } catch (Throwable t) {
      StringBuilder warning = new StringBuilder();
      warning.append("An exception occurred while looking for AutoValue extensions. "
          + "No extensions will function.");
      if (t instanceof ServiceConfigurationError) {
        warning.append(" This may be due to a corrupt jar file in the compiler's classpath.");
      }
      warning.append(" Exception: ")
          .append(t);
      messager.printMessage(Diagnostic.Kind.WARNING, warning.toString(), null);
      extensions = ImmutableSet.of();
    }

    preferKotlin = Boolean.valueOf(processingEnv.getOptions()
        .getOrDefault(OPTION_PREFER_KOTLIN, "false"));

    if (preferKotlin) {
      processor = new KotlinInspectorProcessor();
    } else {
      processor = new JavaInspectorProcessor();
    }
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public Set<String> getSupportedOptions() {
    return Collections.singleton(OPTION_PREFER_KOTLIN);
  }

  @Override public Set<String> getSupportedAnnotationTypes() {
    Set<String> supportedAnnotations = Sets.newLinkedHashSet();
    extensions.forEach(ext -> supportedAnnotations.addAll(ext.applicableAnnotations()));
    return supportedAnnotations;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    InspectorCompilerContext context
        = new InspectorCompilerContext(processingEnv, roundEnv, preferKotlin);
    extensions.forEach(e -> e.initRound(context));
    Set<Element> elements = Sets.newLinkedHashSet();
    for (TypeElement annotation : annotations) {
      elements.addAll(roundEnv.getElementsAnnotatedWith(annotation));
    }
    for (Element element : elements) {
      TypeElement targetClass = (TypeElement) element;
      if (applicable(targetClass)) {
        processor.process(context, targetClass, extensions);
      }
    }

    return false;
  }

  @SuppressWarnings("Duplicates") private boolean implementsSelfValidating(TypeElement type) {
    TypeMirror validatorFactoryType =
        elements.getTypeElement(SelfValidating.class.getCanonicalName())
            .asType();
    TypeMirror typeMirror = type.asType();
    if (!type.getInterfaces()
        .isEmpty() || typeMirror.getKind() != TypeKind.NONE) {
      while (typeMirror.getKind() != TypeKind.NONE) {
        if (searchInterfacesAncestry(typeMirror, validatorFactoryType)) {
          return true;
        }
        type = (TypeElement) typeUtils.asElement(typeMirror);
        typeMirror = type.getSuperclass();
      }
    }
    return false;
  }

  @SuppressWarnings("Duplicates")
  private boolean searchInterfacesAncestry(TypeMirror rootIface, TypeMirror target) {
    TypeElement rootIfaceElement = (TypeElement) typeUtils.asElement(rootIface);
    // check if it implements valid interfaces
    for (TypeMirror iface : rootIfaceElement.getInterfaces()) {
      TypeElement ifaceElement = (TypeElement) typeUtils.asElement(rootIface);
      while (iface.getKind() != TypeKind.NONE) {
        if (typeUtils.isSameType(iface, target)) {
          return true;
        }
        // go up
        if (searchInterfacesAncestry(iface, target)) {
          return true;
        }
        // then move on
        iface = ifaceElement.getSuperclass();
      }
    }
    return false;
  }

  private boolean applicable(TypeElement type) {
    boolean isSelfValidating = implementsSelfValidating(type);

    if (type.getAnnotation(GenerateValidator.class) != null) {
      return true;
    }

    // check that the class contains a public static method returning a Validator
    TypeName typeName = TypeName.get(type.asType());
    ParameterizedTypeName validatorType =
        ParameterizedTypeName.get(ClassName.get(Validator.class), typeName);
    TypeName returnedValidator = null;
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      if (method.getModifiers()
          .contains(STATIC) && !method.getModifiers()
          .contains(PRIVATE)) {
        TypeMirror rType = method.getReturnType();
        TypeName returnType = TypeName.get(rType);
        if (returnType.equals(validatorType)) {
          return checkSelfValidating(isSelfValidating, type);
        }

        if (returnType.equals(validatorType.rawType) || (returnType instanceof ParameterizedTypeName
            && ((ParameterizedTypeName) returnType).rawType.equals(validatorType.rawType))) {
          returnedValidator = returnType;
        }
      }
    }

    if (returnedValidator == null) {
      return false;
    }

    // emit a warning if the user added a method returning a Validator, but not of the right type
    if (returnedValidator instanceof ParameterizedTypeName) {
      ParameterizedTypeName paramReturnType = (ParameterizedTypeName) returnedValidator;
      TypeName argument = paramReturnType.typeArguments.get(0);

      // If the original type uses generics, user's don't have to nest the generic type args
      if (typeName instanceof ParameterizedTypeName) {
        ParameterizedTypeName pTypeName = (ParameterizedTypeName) typeName;
        if (pTypeName.rawType.equals(argument)) {
          return checkSelfValidating(isSelfValidating, type);
        }
      } else {
        messager.printMessage(Diagnostic.Kind.WARNING,
            String.format("Found public static method returning Validator<%s> on %s class. "
                + "Skipping InspectorValidator generation.", argument, type));
      }
    } else {
      messager.printMessage(Diagnostic.Kind.WARNING,
          "Found public static method returning "
              + "Validator with no type arguments, skipping Validator generation.");
    }

    return false;
  }

  private boolean checkSelfValidating(boolean isSelfValidating, TypeElement type) {
    if (isSelfValidating) {
      messager.printMessage(Diagnostic.Kind.WARNING,
          String.format("Found public static method returning Validator on %s class, but "
                  + "it also implements SelfValidating. Skipping InspectorValidator generation.",
              type));
      return false;
    }
    return true;
  }

}
