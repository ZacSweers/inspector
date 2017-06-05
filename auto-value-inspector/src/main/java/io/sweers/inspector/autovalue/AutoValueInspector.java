package io.sweers.inspector.autovalue;

import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.Size;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ArrayTypeName;
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
import com.uber.rave.annotation.MustBeFalse;
import com.uber.rave.annotation.MustBeTrue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.Types;
import io.sweers.inspector.ValidatedBy;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.ValidationQualifier;
import io.sweers.inspector.Validator;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(AutoValueExtension.class) public final class AutoValueInspector
    extends AutoValueExtension {

  @Override public boolean applicable(Context context) {
    // check that the class contains a public static method returning a Validator
    TypeElement type = context.autoValueClass();
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
    Messager messager = context.processingEnvironment()
        .getMessager();
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

  @Override public String generateClass(Context context,
      String className,
      String classToExtend,
      boolean isFinal) {

    List<Property> properties = readProperties(context.properties());
    Map<String, TypeName> types = convertPropertiesToTypes(context.properties());

    List<? extends TypeParameterElement> typeParams = context.autoValueClass()
        .getTypeParameters();
    boolean shouldCreateGenerics = typeParams != null && typeParams.size() > 0;

    ClassName classNameClass = ClassName.get(context.packageName(), className);
    ClassName autoValueClass = ClassName.get(context.autoValueClass());
    TypeName autoValueClassName = autoValueClass;
    TypeVariableName[] genericTypeNames = null;

    TypeName superclass;

    if (shouldCreateGenerics) {
      genericTypeNames = new TypeVariableName[typeParams.size()];
      for (int i = 0; i < typeParams.size(); i++) {
        genericTypeNames[i] = TypeVariableName.get(typeParams.get(i));
      }

      superclass = ParameterizedTypeName.get(ClassName.get(context.packageName(), classToExtend),
          (TypeName[]) genericTypeNames);
      autoValueClassName = ParameterizedTypeName.get(autoValueClass, (TypeName[]) genericTypeNames);
    } else {
      superclass = TypeVariableName.get(classToExtend);
    }

    TypeSpec validator =
        createValidator(context, classNameClass, autoValueClassName, genericTypeNames, properties);

    TypeSpec.Builder subclass = TypeSpec.classBuilder(className)
        .superclass(superclass)
        .addType(validator)
        .addMethod(generateConstructor(types));

    if (shouldCreateGenerics) {
      subclass.addTypeVariables(Arrays.asList(genericTypeNames));
    }

    if (isFinal) {
      subclass.addModifiers(FINAL);
    } else {
      subclass.addModifiers(ABSTRACT);
    }

    return JavaFile.builder(context.packageName(), subclass.build())
        .build()
        .toString();
  }

  private TypeSpec createValidator(Context context,
      ClassName className,
      TypeName autoValueClassName,
      TypeVariableName[] genericTypeNames,
      List<Property> properties) {
    TypeName validatorClass =
        ParameterizedTypeName.get(ClassName.get(Validator.class), autoValueClassName);

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
        constructor.addStatement("this.$N = adapter($N, \"$L\")",
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

    TypeSpec.Builder classBuilder = TypeSpec.classBuilder("InspectorValidator")
        .addModifiers(PUBLIC, STATIC, FINAL)
        .superclass(validatorClass)
        .addFields(validators.values())
        .addMethod(constructor.build())
        .addMethod(createValidationMethod(context, autoValueClassName, validators));

    if (genericTypeNames != null) {
      classBuilder.addTypeVariables(Arrays.asList(genericTypeNames));
    }

    if (needsAdapterMethod) {
      classBuilder.addMethod(createAdapterMethod(autoValueClassName));
    }

    return classBuilder.build();
  }

  private static MethodSpec createValidationMethod(Context context,
      TypeName autoValueClassName,
      ImmutableMap<Property, FieldSpec> validators) {
    String valueName = "value";
    ParameterSpec value = ParameterSpec.builder(autoValueClassName, valueName)
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
          addAndroidChecks(validateMethod, prop, name);
          addRaveChecks(validateMethod, prop.element, prop.methodName, value);
        });

    checkFinalMethods(context, validateMethod, value);

    return validateMethod.build();
  }

  private static void checkFinalMethods(Context context,
      MethodSpec.Builder validateMethod,
      ParameterSpec value) {
    TypeElement type = context.autoValueClass();
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      if (method.getModifiers()
          .contains(FINAL) && !method.getModifiers()
          .contains(PRIVATE) && !method.getModifiers()
          .contains(STATIC)) {
        addRaveChecks(validateMethod,
            method,
            method.getSimpleName()
                .toString(),
            value);
      }
    }
  }

  private static void addAndroidChecks(MethodSpec.Builder validateMethod,
      Property prop,
      String variableName) {
    IntRange intRange = prop.annotation(IntRange.class);
    if (intRange != null) {
      long from = intRange.from();
      long to = intRange.to();
      if (from != Long.MIN_VALUE) {
        validateMethod.beginControlFlow("if ($L < $L)", variableName, from)
            .addStatement("throw new $T(\"$L must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                from,
                variableName)
            .endControlFlow();
      }
      if (to != Long.MAX_VALUE) {
        validateMethod.beginControlFlow("else if ($L > $L)", variableName, to)
            .addStatement("throw new $T(\"$L must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                to,
                variableName)
            .endControlFlow();
      }
    }
    FloatRange floatRange = prop.annotation(FloatRange.class);
    if (floatRange != null) {
      double from = floatRange.from();
      double to = floatRange.to();
      if (from != Double.NEGATIVE_INFINITY) {
        validateMethod.beginControlFlow("if ($L < $L)", variableName, from)
            .addStatement("throw new $T(\"$L must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                from,
                variableName)
            .endControlFlow();
      }
      if (to != Double.POSITIVE_INFINITY) {
        validateMethod.beginControlFlow("else if ($L > $L)", variableName, to)
            .addStatement("throw new $T(\"$L must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                to,
                variableName)
            .endControlFlow();
      }
    }
    Size size = prop.annotation(Size.class);
    if (size != null) {
      String sizeVar = variableName + "Size";
      if (prop.type instanceof ArrayTypeName) {
        validateMethod.addStatement("int $L = $L.length", sizeVar, variableName);
      } else if (prop.type instanceof ParameterizedTypeName) {
        // Assume it's a collection or map
        validateMethod.addStatement("int $L = $L.size()", sizeVar, variableName);
      }
      long exact = size.value();
      long min = size.min();
      long max = size.max();
      long multiple = size.multiple();
      if (exact != -1) {
        validateMethod.beginControlFlow("if ($L != $L)", sizeVar, exact)
            .addStatement("throw new $T(\"$L's size must be exactly $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                exact,
                sizeVar)
            .endControlFlow();
      }
      if (min != Long.MIN_VALUE) {
        validateMethod.beginControlFlow("if ($L < $L)", sizeVar, min)
            .addStatement("throw new $T(\"$L's size must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                min,
                sizeVar)
            .endControlFlow();
      }
      if (max != Long.MAX_VALUE) {
        validateMethod.beginControlFlow("if ($L > $L)", sizeVar, max)
            .addStatement("throw new $T(\"$L's size must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                max,
                sizeVar)
            .endControlFlow();
      }
      if (multiple != 1) {
        validateMethod.beginControlFlow("if ($L % $L != 0)", sizeVar, multiple)
            .addStatement("throw new $T(\"$L's size must be a multiple of $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                multiple,
                sizeVar)
            .endControlFlow();
      }
    }

    // TODO IntDef and StringDef
  }

  private static void addRaveChecks(MethodSpec.Builder validateMethod,
      ExecutableElement element,
      String methodName,
      ParameterSpec value) {
    MustBeTrue mustBeTrue = element.getAnnotation(MustBeTrue.class);
    if (mustBeTrue != null) {
      TypeMirror rType = element.getReturnType();
      TypeName returnType = TypeName.get(rType);
      if (returnType != TypeName.BOOLEAN && returnType != TypeName.BOOLEAN.box()) {
        throw new IllegalArgumentException(
            "@MustBeTrue can only be used on boolean return types but "
                + methodName
                + " returns "
                + returnType);
      }
      validateMethod.beginControlFlow("if (!$N.$L())", value, methodName)
          .addStatement("throw new $T(\"$L must be true but is false\")",
              ValidationException.class,
              methodName)
          .endControlFlow();
    }
    MustBeFalse mustBeFalse = element.getAnnotation(MustBeFalse.class);
    if (mustBeFalse != null) {
      TypeMirror rType = element.getReturnType();
      TypeName returnType = TypeName.get(rType);
      if (returnType != TypeName.BOOLEAN && returnType != TypeName.BOOLEAN.box()) {
        throw new IllegalArgumentException(
            "@MustBeFalse can only be used on boolean return types but "
                + methodName
                + " returns "
                + returnType);
      }
      validateMethod.beginControlFlow("if ($N.$L())", value, methodName)
          .addStatement("throw new $T(\"$L must be false but is true\")",
              ValidationException.class,
              methodName)
          .endControlFlow();
    }
  }

  @Nullable private static <T extends Annotation> T findAnnotation(Set<Annotation> annotations,
      Class<T> clazz) {
    for (Annotation annotation : annotations) {
      if (annotation.getClass()
          .equals(clazz)) {
        return (T) annotation;
      }
    }
    return null;
  }

  private static int getTypeIndexInArray(TypeVariableName[] array, TypeName typeName) {
    return Arrays.binarySearch(array, typeName, (typeName1, t1) -> typeName1.equals(t1) ? 0 : -1);
  }

  private static MethodSpec createAdapterMethod(TypeName autoValueClassName) {
    ParameterSpec inspector = ParameterSpec.builder(Inspector.class, "inspector")
        .build();
    ParameterSpec methodName = ParameterSpec.builder(String.class, "methodName")
        .build();
    return MethodSpec.methodBuilder("adapter")
        .addModifiers(PRIVATE)
        .addParameters(ImmutableSet.of(inspector, methodName))
        .returns(Validator.class)
        .addCode(CodeBlock.builder()
            .beginControlFlow("try")
            .addStatement("$T method = $T.class.getDeclaredMethod($N)",
                Method.class,
                autoValueClassName,
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

  private static MethodSpec generateConstructor(Map<String, TypeName> properties) {
    List<ParameterSpec> params = Lists.newArrayList();
    for (Map.Entry<String, TypeName> entry : properties.entrySet()) {
      params.add(ParameterSpec.builder(entry.getValue(), entry.getKey())
          .build());
    }

    MethodSpec.Builder builder = MethodSpec.constructorBuilder()
        .addParameters(params);

    StringBuilder superFormat = new StringBuilder("super(");
    for (int i = properties.size(); i > 0; i--) {
      superFormat.append("$N");
      if (i > 1) superFormat.append(", ");
    }
    superFormat.append(")");
    builder.addStatement(superFormat.toString(),
        properties.keySet()
            .toArray());

    return builder.build();
  }

  /**
   * Converts the ExecutableElement properties to TypeName properties
   */
  static Map<String, TypeName> convertPropertiesToTypes(Map<String, ExecutableElement> properties) {
    Map<String, TypeName> types = new LinkedHashMap<>();
    for (Map.Entry<String, ExecutableElement> entry : properties.entrySet()) {
      ExecutableElement el = entry.getValue();
      types.put(entry.getKey(), TypeName.get(el.getReturnType()));
    }
    return types;
  }

  public static List<Property> readProperties(Map<String, ExecutableElement> properties) {
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

  public static class Property {
    final String methodName;
    final String humanName;
    final ExecutableElement element;
    final TypeName type;
    final ImmutableSet<String> annotations;
    final TypeMirror validator;

    public Property(String humanName, ExecutableElement element) {
      this.methodName = element.getSimpleName()
          .toString();
      this.humanName = humanName;
      this.element = element;

      type = TypeName.get(element.getReturnType());
      annotations = buildAnnotations(element);

      validator = getAnnotationValue(element, ValidatedBy.class);
    }

    public static TypeMirror getAnnotationValue(Element foo, Class<?> annotation) {
      AnnotationMirror am = getAnnotationMirror(foo, annotation);
      if (am == null) {
        return null;
      }
      AnnotationValue av = getAnnotationValue(am, "value");
      return av == null ? null : (TypeMirror) av.getValue();
    }

    private static AnnotationMirror getAnnotationMirror(Element typeElement, Class<?> clazz) {
      String clazzName = clazz.getName();
      for (AnnotationMirror m : typeElement.getAnnotationMirrors()) {
        if (m.getAnnotationType()
            .toString()
            .equals(clazzName)) {
          return m;
        }
      }
      return null;
    }

    private static AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror,
        String key) {
      Map<? extends ExecutableElement, ? extends AnnotationValue> values =
          annotationMirror.getElementValues();
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values
          .entrySet()) {
        if (entry.getKey()
            .getSimpleName()
            .toString()
            .equals(key)) {
          return entry.getValue();
        }
      }
      return null;
    }

    @Nullable public <T extends Annotation> T annotation(Class<T> annotation) {
      return element.getAnnotation(annotation);
    }

    public ValidatedBy validatedBy() {
      return element.getAnnotation(ValidatedBy.class);
    }

    public boolean shouldValidate() {
      return element.getAnnotation(InspectorIgnored.class) == null;
    }

    public boolean shouldNullCheck() {
      return !type.isPrimitive() && !annotations.contains("Nullable");
    }

    private ImmutableSet<String> buildAnnotations(ExecutableElement element) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();

      List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();
      for (AnnotationMirror annotation : annotations) {
        builder.add(annotation.getAnnotationType()
            .asElement()
            .getSimpleName()
            .toString());
      }

      return builder.build();
    }
  }
}
