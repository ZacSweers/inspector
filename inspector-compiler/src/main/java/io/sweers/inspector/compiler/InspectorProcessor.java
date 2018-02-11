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
import io.sweers.inspector.ValidatedBy;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.ValidationQualifier;
import io.sweers.inspector.Validator;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
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

@AutoService(Processor.class) public final class InspectorProcessor extends AbstractProcessor {

  // Depending on how this InspectorProcessor was constructed, we might already have a list of
  // extensions when init() is run, or, if `extensions` is null, we have a ClassLoader that will be
  // used to get the list using the ServiceLoader API.
  private Set<InspectorExtension> extensions;
  private final ClassLoader loaderForExtensions;
  private Messager messager;
  private Filer filer;
  private Elements elements;
  private javax.lang.model.util.Types typeUtils;

  public InspectorProcessor() {
    this(InspectorProcessor.class.getClassLoader());
  }

  @VisibleForTesting InspectorProcessor(ClassLoader loaderForExtensions) {
    this.extensions = null;
    this.loaderForExtensions = loaderForExtensions;
  }

  @VisibleForTesting public InspectorProcessor(Iterable<? extends InspectorExtension> extensions) {
    this.extensions = ImmutableSet.copyOf(extensions);
    this.loaderForExtensions = null;
  }

  @Override public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
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
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public Set<String> getSupportedAnnotationTypes() {
    Set<String> supportedAnnotations = Sets.newLinkedHashSet();
    extensions.forEach(ext -> supportedAnnotations.addAll(ext.applicableAnnotations()));
    return supportedAnnotations;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<Element> elements = Sets.newLinkedHashSet();
    for (TypeElement annotation : annotations) {
      elements.addAll(roundEnv.getElementsAnnotatedWith(annotation));
    }
    for (Element element : elements) {
      TypeElement targetClass = (TypeElement) element;
      if (applicable(targetClass)) {
        generateClass(targetClass);
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

  private Map<String, ExecutableElement> getProperties(TypeElement targetClass) {
    Map<String, ExecutableElement> elements = Maps.newLinkedHashMap();
    for (ExecutableElement method : ElementFilter.methodsIn(targetClass.getEnclosedElements())) {
      if (!method.getModifiers()
          .contains(PRIVATE) && !method.getModifiers()
          .contains(STATIC) && method.getAnnotation(InspectorIgnored.class) == null) {
        elements.put(method.getSimpleName()
            .toString(), method);
      }
    }
    return elements;
  }

  private void generateClass(TypeElement targetClass) {

    Map<String, ExecutableElement> propertiesMap = getProperties(targetClass);
    List<Property> properties = readProperties(propertiesMap);

    List<? extends TypeParameterElement> typeParams = targetClass.getTypeParameters();
    boolean shouldCreateGenerics = typeParams != null && typeParams.size() > 0;

    String packageName = elements.getPackageOf(targetClass)
        .getQualifiedName()
        .toString();
    String simpleName = targetClass.getSimpleName()
        .toString();

    ClassName initialClassName = ClassName.get(targetClass);
    TypeVariableName[] genericTypeNames = null;
    TypeName targetClassName = initialClassName;

    if (shouldCreateGenerics) {
      genericTypeNames = new TypeVariableName[typeParams.size()];
      for (int i = 0; i < typeParams.size(); i++) {
        genericTypeNames[i] = TypeVariableName.get(typeParams.get(i));
      }
      targetClassName = ParameterizedTypeName.get(initialClassName, genericTypeNames);
    }

    TypeSpec.Builder validator =
        createValidator(simpleName, targetClassName, genericTypeNames, properties);

    validator.addModifiers(FINAL);

    try {
      JavaFile.builder(packageName, validator.build())
          .skipJavaLangImports(true)
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TypeSpec.Builder createValidator(String simpleName,
      TypeName targetClassName,
      TypeVariableName[] genericTypeNames,
      List<Property> properties) {
    TypeName validatorClass =
        ParameterizedTypeName.get(ClassName.get(Validator.class), targetClassName);

    ImmutableMap<Property, FieldSpec> validators = createFields(properties);

    ParameterSpec inspector = ParameterSpec.builder(Inspector.class, "inspector")
        .build();
    ParameterSpec type = null;

    MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
        .addModifiers(PUBLIC)
        .addParameter(inspector);

    if (genericTypeNames != null) {
      type = ParameterSpec.builder(Type[].class, "types")
          .build();
      constructor.addParameter(type);
    }

    boolean needsValidatorMethod = false;
    for (Map.Entry<Property, FieldSpec> entry : validators.entrySet()) {
      Property prop = entry.getKey();
      FieldSpec field = entry.getValue();

      boolean usesValidationQualifier = false;
      for (AnnotationMirror annotationMirror : prop.element.getAnnotationMirrors()) {
        Element annotationType = annotationMirror.getAnnotationType()
            .asElement();
        if (annotationType.getAnnotation(ValidationQualifier.class) != null) {
          usesValidationQualifier = true;
          needsValidatorMethod = true;
        }
      }
      ValidatedBy validatedBy = prop.validatedBy();
      if (validatedBy != null) {
        Set<TypeElement> validatorClasses = getValueFieldOfClasses(prop.validatedByMirror())
            .stream()
            .map(MoreTypes::asTypeElement)
            .collect(toImmutableSet());
        if (validatorClasses.isEmpty()) {
          messager.printMessage(Diagnostic.Kind.ERROR, "No validator classes specified in @ValidatedBy annotation!", prop.element);
        } else if (validatorClasses.size() == 1) {
          constructor.addStatement("this.$N = new $T()", field, ClassName.get(validatorClasses.iterator().next()));
        } else {
          String validatorsString = String.join(", ", validatorClasses
              .stream()
              .map(c -> "new $T()")
              .collect(toList()));
          ClassName[] arguments = validatorClasses
              .stream()
              .map(ClassName::get)
              .toArray(ClassName[]::new);
          CodeBlock validatorsCodeBlock = CodeBlock.of(validatorsString, (Object[]) arguments);
          constructor.addStatement("this.$N = $T.<$T>of($L)", field,
              CompositeValidator.class,
              prop.type,
              validatorsCodeBlock);
        }
      } else if (usesValidationQualifier) {
        constructor.addStatement("this.$N = validator($N, \"$L\")",
            field,
            inspector,
            prop.methodName);
      } else if (genericTypeNames != null && prop.type instanceof ParameterizedTypeName) {
        ParameterizedTypeName typeName = ((ParameterizedTypeName) prop.type);
        constructor.addStatement("this.$N = $N.validator($T.newParameterizedType($T.class, "
                + "$N[$L]))",
            field,
            inspector,
            Types.class,
            typeName.rawType,
            type,
            getTypeIndexInArray(genericTypeNames, typeName.typeArguments.get(0)));
      } else if (genericTypeNames != null
          && getTypeIndexInArray(genericTypeNames, prop.type) >= 0) {
        constructor.addStatement("this.$N = $N.validator($N[$L])",
            field,
            inspector,
            type,
            getTypeIndexInArray(genericTypeNames, prop.type));
      } else {
        constructor.addStatement("this.$N = $N.validator($L)",
            field,
            inspector,
            makeType(prop.type));
      }
    }

    TypeSpec.Builder classBuilder = TypeSpec.classBuilder("Validator_" + simpleName)
        .addModifiers(FINAL)
        .superclass(validatorClass)
        .addFields(validators.values())
        .addMethod(constructor.build())
        .addMethod(createValidationMethod(targetClassName, validators));

    if (genericTypeNames != null) {
      classBuilder.addTypeVariables(Arrays.asList(genericTypeNames));
    }

    if (needsValidatorMethod) {
      classBuilder.addMethod(createAdapterMethod(targetClassName));
    }

    return classBuilder;
  }

  /**
   * Returns the contents of a {@code Class[]}-typed "value" field in a given {@code annotationMirror}.
   */
  private ImmutableSet<DeclaredType> getValueFieldOfClasses(AnnotationMirror annotationMirror) {
    return getAnnotationValue(annotationMirror, "value")
        .accept(
            new SimpleAnnotationValueVisitor8<ImmutableSet<DeclaredType>, Void>() {
              @Override
              public ImmutableSet<DeclaredType> visitType(TypeMirror typeMirror, Void v) {
                return ImmutableSet.of(MoreTypes.asDeclared(typeMirror));
              }

              @Override
              public ImmutableSet<DeclaredType> visitArray(
                  List<? extends AnnotationValue> values, Void v) {
                return values
                    .stream()
                    .flatMap(value -> value.accept(this, null).stream())
                    .collect(toImmutableSet());
              }
            },
            null);
  }

  private MethodSpec createValidationMethod(TypeName targetClassName,
      ImmutableMap<Property, FieldSpec> validators) {
    String valueName = "value";
    ParameterSpec value = ParameterSpec.builder(targetClassName, valueName)
        .build();
    MethodSpec.Builder validateMethod = MethodSpec.methodBuilder("validate")
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .addParameter(value)
        .addException(ValidationException.class);

    // Go through validators
    NameAllocator allocator = new NameAllocator();
    validators.entrySet()
        .stream()
        .filter(entry -> entry.getKey()
            .shouldValidate())
        .forEach(entry -> {
          Property prop = entry.getKey();
          FieldSpec validator = entry.getValue();
          String name = allocator.newName(entry.getKey().methodName);
          validateMethod.addComment("Begin validation for \"$L()\"", prop.methodName)
              .addStatement("$T $L = $N.$L()", prop.type, name, value, prop.methodName)
              .addCode("\n");
          extensions.stream()
              .sorted(Comparator.comparing(InspectorExtension::priority))
              .filter(e -> e.applicable(prop))
              .forEach(e -> {
                CodeBlock block = e.generateValidation(prop, name, value);
                if (block != null) {
                  validateMethod.addComment("Validations contributed by $S", e.toString())
                      .addCode(block);
                }
              });
          validateMethod.addStatement("$N.validate($L)", validator, name)
              .addCode("\n");
        });

    return validateMethod.build();
  }

  private static int getTypeIndexInArray(TypeVariableName[] array, TypeName typeName) {
    return Arrays.binarySearch(array, typeName, (typeName1, t1) -> typeName1.equals(t1) ? 0 : -1);
  }

  private static MethodSpec createAdapterMethod(TypeName targetClassName) {
    ParameterSpec inspector = ParameterSpec.builder(Inspector.class, "inspector")
        .build();
    ParameterSpec methodName = ParameterSpec.builder(String.class, "methodName")
        .build();
    return MethodSpec.methodBuilder("validator")
        .addModifiers(PRIVATE)
        .addParameters(ImmutableSet.of(inspector, methodName))
        .returns(Validator.class)
        .addCode(CodeBlock.builder()
            .beginControlFlow("try")
            .addStatement("$T method = $T.class.getDeclaredMethod($N)",
                Method.class,
                targetClassName,
                methodName)
            .addStatement("$T<$T> annotations = new $T<>()",
                Set.class,
                Annotation.class,
                LinkedHashSet.class)
            .beginControlFlow("for ($T annotation : method.getAnnotations())", Annotation.class)
            .beginControlFlow("if (annotation.annotationType().isAnnotationPresent($T.class))",
                ValidationQualifier.class)
            .addStatement("annotations.add(annotation)")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return $N.validator(method.getGenericReturnType(), annotations)",
                inspector)
            .nextControlFlow("catch ($T e)", NoSuchMethodException.class)
            .addStatement("throw new RuntimeException(\"No method named \" + $N, e)", methodName)
            .endControlFlow()
            .build())
        .build();
  }

  private static ImmutableMap<Property, FieldSpec> createFields(List<Property> properties) {
    ImmutableMap.Builder<Property, FieldSpec> fields = ImmutableMap.builder();

    for (Property property : properties) {
      TypeName type = property.type.isPrimitive() ? property.type.box() : property.type;
      ParameterizedTypeName adp = ParameterizedTypeName.get(ClassName.get(Validator.class), type);
      fields.put(property,
          FieldSpec.builder(adp, property.humanName + "Validator", PRIVATE, FINAL)
              .build());
    }

    return fields.build();
  }

  private static List<Property> readProperties(Map<String, ExecutableElement> properties) {
    List<Property> values = new ArrayList<>();
    for (Map.Entry<String, ExecutableElement> entry : properties.entrySet()) {
      if (entry.getValue()
          .getReturnType() instanceof NoType) {
        // Covers things like void types
        continue;
      }
      values.add(new Property(entry.getKey(), entry.getValue()));
    }
    return values;
  }

  private static CodeBlock makeType(TypeName type) {
    CodeBlock.Builder block = CodeBlock.builder();
    if (type instanceof ParameterizedTypeName) {
      ParameterizedTypeName pType = (ParameterizedTypeName) type;
      block.add("$T.newParameterizedType($T.class", Types.class, pType.rawType);
      for (TypeName typeArg : pType.typeArguments) {
        if (typeArg instanceof ParameterizedTypeName) {
          block.add(", $L", makeType(typeArg));
        } else if (typeArg instanceof WildcardTypeName) {
          WildcardTypeName wildcard = (WildcardTypeName) typeArg;
          TypeName target;
          String method;
          if (wildcard.lowerBounds.size() == 1) {
            target = wildcard.lowerBounds.get(0);
            method = "supertypeOf";
          } else if (wildcard.upperBounds.size() == 1) {
            target = wildcard.upperBounds.get(0);
            method = "subtypeOf";
          } else {
            throw new IllegalArgumentException(
                "Unrepresentable wildcard type. Cannot have more than one bound: " + wildcard);
          }
          block.add(", $T.$L($T.class)", Types.class, method, target);
        } else {
          block.add(", $T.class", typeArg);
        }
      }
      block.add(")");
    } else {
      block.add("$T.class", type);
    }
    return block.build();
  }
}
