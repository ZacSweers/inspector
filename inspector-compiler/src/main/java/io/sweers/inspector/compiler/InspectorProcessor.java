package io.sweers.inspector.compiler;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
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
import io.sweers.inspector.Inspector;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.Types;
import io.sweers.inspector.ValidatedBy;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.ValidationQualifier;
import io.sweers.inspector.Validator;
import io.sweers.inspector.compiler.plugins.InspectorExtensionService;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class) public final class InspectorProcessor extends AbstractProcessor {

  private final Set<InspectorExtension> extensions = InspectorExtensionService.newInstance()
      .get();
  private Messager messager;
  private Filer filer;
  private Elements elements;

  @Override public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
    elements = processingEnv.getElementUtils();
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_8;
  }

  @Override public Set<String> getSupportedAnnotationTypes() {
    Set<String> supportedAnnotations = Sets.newLinkedHashSet();
    extensions.forEach(ext -> supportedAnnotations.addAll(ext.applicableAnnotations()));
    return supportedAnnotations;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AutoValue.class);
    for (Element element : elements) {
      TypeElement targetClass = (TypeElement) element;
      if (applicable(targetClass)) {
        generateClass(targetClass);
      }
    }

    return false;
  }

  private boolean applicable(TypeElement type) {
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
          return true;
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
          return true;
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

    ClassName targetClassName = ClassName.get(targetClass);
    TypeVariableName[] genericTypeNames = null;

    if (shouldCreateGenerics) {
      genericTypeNames = new TypeVariableName[typeParams.size()];
      for (int i = 0; i < typeParams.size(); i++) {
        genericTypeNames[i] = TypeVariableName.get(typeParams.get(i));
      }
    }

    TypeSpec.Builder validator =
        createValidator(simpleName, targetClassName, genericTypeNames, properties);

    if (shouldCreateGenerics) {
      validator.addTypeVariables(Arrays.asList(genericTypeNames));
    }

    validator.addModifiers(FINAL);

    try {
      JavaFile.builder(packageName, validator.build())
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

    boolean needsAdapterMethod = false;
    for (Map.Entry<Property, FieldSpec> entry : validators.entrySet()) {
      Property prop = entry.getKey();
      FieldSpec field = entry.getValue();

      boolean usesJsonQualifier = false;
      for (AnnotationMirror annotationMirror : prop.element.getAnnotationMirrors()) {
        Element annotationType = annotationMirror.getAnnotationType()
            .asElement();
        if (annotationType.getAnnotation(ValidationQualifier.class) != null) {
          usesJsonQualifier = true;
          needsAdapterMethod = true;
        }
      }
      ValidatedBy validatedBy = prop.validatedBy();
      if (validatedBy != null) {
        try {
          validatedBy.value();
        } catch (MirroredTypeException e) {
          // This is apparently how to get classes from annotations
          // Let's never speak of this again
          constructor.addStatement("this.$N = new $T()", field, ClassName.get(e.getTypeMirror()));
        }
      } else if (usesJsonQualifier) {
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

    if (needsAdapterMethod) {
      classBuilder.addMethod(createAdapterMethod(targetClassName));
    }

    return classBuilder;
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
          validateMethod.addStatement("$T $L = $N.$L()", prop.type, name, value, prop.methodName);
          if (prop.shouldNullCheck()) {
            validateMethod.beginControlFlow("if ($L == null)", name)
                .addStatement("throw new $T($S)",
                    ValidationException.class,
                    prop.methodName + "() is not null but returns a null")
                .endControlFlow();
          }
          validateMethod.addStatement("$N.validate($L)", validator, name);
          extensions.stream()
              .filter(e -> e.applicable(prop.element))
              .forEach(e -> {
                CodeBlock block = e.generateValidation(prop, name, value);
                if (block != null) {
                  validateMethod.addCode(block);
                }
              });
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
