package io.sweers.inspector.factorycompiler;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class) public final class InspectorFactoryProcessor
    extends AbstractProcessor {

  private static final ClassName ADAPTER_CLASS_NAME = ClassName.get(Validator.class);
  private static final ParameterSpec TYPE_SPEC = ParameterSpec.builder(Type.class, "type")
      .build();
  private static final WildcardTypeName WILDCARD_TYPE_NAME =
      WildcardTypeName.subtypeOf(Annotation.class);
  private static final ParameterSpec ANNOTATIONS_SPEC =
      ParameterSpec.builder(ParameterizedTypeName.get(ClassName.get(Set.class), WILDCARD_TYPE_NAME),
          "annotations")
          .build();
  private static final ParameterSpec INSPECTOR_SPEC =
      ParameterSpec.builder(Inspector.class, "inspector")
          .build();
  private static final ParameterizedTypeName FACTORY_RETURN_TYPE_NAME =
      ParameterizedTypeName.get(ADAPTER_CLASS_NAME, WildcardTypeName.subtypeOf(TypeName.OBJECT));

  private Elements elementUtils;
  private Types typeUtils;

  @Override public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.elementUtils = processingEnv.getElementUtils();
    this.typeUtils = processingEnv.getTypeUtils();
  }

  @Override public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(InspectorFactory.class.getName());
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<TypeElement> factories = roundEnv.getElementsAnnotatedWith(InspectorFactory.class)
        .stream()
        .map((Function<Element, TypeElement>) element -> (TypeElement) element)
        .collect(Collectors.toSet());

    for (TypeElement factory : factories) {
      if (!implementsValidatorFactory(factory)) {
        error(factory, "Must implement Validator.Factory!");
      }
      List<TypeElement> validationTargets =
          getTargetClasses(factory.getAnnotation(InspectorFactory.class)).flatMap(targetClass ->
              roundEnv.getElementsAnnotatedWith(
              targetClass)
              .stream())
              .map((Function<Element, TypeElement>) element -> {
                if (!(element instanceof TypeElement)) {
                  throw new UnsupportedOperationException(
                      "InspectorFactories can only operate on annotated types.");
                }
                return (TypeElement) element;
              })
              .collect(Collectors.toList());

      String adapterName = classNameOf(factory);
      String packageName = packageNameOf(factory);

      TypeSpec validatorFactory =
          createValidatorFactory(validationTargets, packageName, adapterName);
      JavaFile file = JavaFile.builder(packageName, validatorFactory)
          .build();
      try {
        file.writeTo(processingEnv.getFiler());
      } catch (IOException e) {
        processingEnv.getMessager()
            .printMessage(ERROR, "Failed to write ValidatorFactory: " + e.getLocalizedMessage());
      }
    }

    return false;
  }

  private TypeSpec createValidatorFactory(List<TypeElement> elements,
      String packageName,
      String factoryName) {
    TypeSpec.Builder factory =
        TypeSpec.classBuilder(ClassName.get(packageName, "InspectorFactory_" + factoryName));
    factory.addModifiers(PUBLIC, FINAL);
    factory.superclass(ClassName.get(packageName, factoryName));

    ParameterSpec type = TYPE_SPEC;
    ParameterSpec annotations = ANNOTATIONS_SPEC;
    ParameterSpec moshi = INSPECTOR_SPEC;

    MethodSpec.Builder create = MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC)
        .addAnnotation(Override.class)
        .addParameters(ImmutableSet.of(type, annotations, moshi))
        .returns(FACTORY_RETURN_TYPE_NAME);

    CodeBlock.Builder classes = null;
    CodeBlock.Builder generics = null;

    int numGenerics = 0;
    int numClasses = 0;

    // Avoid providing an adapter for an annotated type.
    create.addStatement("if (!$N.isEmpty()) return null", annotations);

    for (TypeElement element : elements) {
      TypeName elementTypeName = TypeName.get(element.asType());

      if (elementTypeName instanceof ParameterizedTypeName) {
        if (generics == null) {
          generics = CodeBlock.builder()
              .beginControlFlow("if ($N instanceof $T)", type, ParameterizedType.class)
              .addStatement("$T rawType = (($T) $N).getRawType()",
                  Type.class,
                  ParameterizedType.class,
                  type);
        }

        addControlFlowGeneric(generics, elementTypeName, element, numGenerics);
        numGenerics++;
      } else {
        ExecutableElement jsonAdapterMethod = getValidatorMethod(element);
        if (jsonAdapterMethod != null) {
          if (classes == null) {
            classes = CodeBlock.builder();
          }

          addControlFlow(classes, CodeBlock.of("$N", type), elementTypeName, numClasses);
          numClasses++;

          if (jsonAdapterMethod.getParameters()
              .size() == 0) {
            classes.addStatement("return $T.$L()", element, jsonAdapterMethod.getSimpleName());
          } else {
            classes.addStatement("return $T.$L($N)",
                element,
                jsonAdapterMethod.getSimpleName(),
                moshi);
          }
        }
      }
    }

    if (generics != null) {
      generics.endControlFlow();
      generics.addStatement("return null");
      generics.endControlFlow();
      create.addCode(generics.build());
    }

    if (classes != null) {
      classes.endControlFlow();
      create.addCode(classes.build());
    }

    create.addStatement("return null");
    factory.addMethod(create.build());
    return factory.build();
  }

  private void addControlFlowGeneric(CodeBlock.Builder block,
      TypeName elementTypeName,
      Element element,
      int numGenerics) {
    ExecutableElement validatorMethod = getValidatorMethod(element);
    if (validatorMethod != null) {
      TypeName typeName = ((ParameterizedTypeName) elementTypeName).rawType;
      CodeBlock typeBlock = CodeBlock.of("rawType");

      addControlFlow(block, typeBlock, typeName, numGenerics);

      if (validatorMethod.getParameters().size() > 1) {
        block.addStatement("return $L.$L($N, (($T) $N).getActualTypeArguments())",
            element.getSimpleName(),
            validatorMethod.getSimpleName(),
            INSPECTOR_SPEC,
            ParameterizedType.class,
            TYPE_SPEC);
      }
    }
  }

  private void addControlFlow(CodeBlock.Builder block,
      CodeBlock typeBlock,
      TypeName elementTypeName,
      int pos) {
    if (pos == 0) {
      block.beginControlFlow("if ($L.equals($T.class))", typeBlock, elementTypeName);
    } else {
      block.nextControlFlow("else if ($L.equals($T.class))", typeBlock, elementTypeName);
    }
  }

  @Nullable private ExecutableElement getValidatorMethod(Element element) {
    ParameterizedTypeName validatorType =
        ParameterizedTypeName.get(ClassName.get(Validator.class), TypeName.get(element.asType()));
    for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
      if (method.getModifiers()
          .contains(Modifier.STATIC) && method.getModifiers()
          .contains(PUBLIC)) {
        TypeMirror rType = method.getReturnType();
        TypeName returnType = TypeName.get(rType);
        if (returnType.equals(validatorType)) {
          return method;
        }
      }
    }
    return null;
  }

  private Stream<TypeElement> getTargetClasses(InspectorFactory factory) {
    try {
      factory.include();
    } catch (MirroredTypesException e) {
      //noinspection Convert2Lambda this doesn't work on CI as a lambda/method ref ಠ_ಠ
      return e.getTypeMirrors()
          .stream()
          .map((Function<TypeMirror, String>) TypeMirror::toString)
          .map(name -> elementUtils.getTypeElement(name));
    }
    throw new RuntimeException(
        "Could not inspect factory includes. Java annotation processing is weird.");
  }

  /**
   * Returns the name of the given type, including any enclosing types but not the package.
   */
  private static String classNameOf(TypeElement type) {
    String name = type.getQualifiedName()
        .toString();
    String pkgName = packageNameOf(type);
    return pkgName.isEmpty() ? name : name.substring(pkgName.length() + 1);
  }

  /**
   * Returns the name of the package that the given type is in. If the type is in the default
   * (unnamed) package then the name is the empty string.
   */
  private static String packageNameOf(TypeElement type) {
    while (true) {
      Element enclosing = type.getEnclosingElement();
      if (enclosing instanceof PackageElement) {
        return ((PackageElement) enclosing).getQualifiedName()
            .toString();
      }
      type = (TypeElement) enclosing;
    }
  }

  private void error(Element element, String message, Object... args) {
    if (args.length > 0) {
      message = String.format(message, args);
    }
    processingEnv.getMessager()
        .printMessage(ERROR, message, element);
  }

  @SuppressWarnings("Duplicates") private boolean implementsValidatorFactory(TypeElement type) {
    TypeMirror validatorFactoryType =
        elementUtils.getTypeElement(Validator.Factory.class.getCanonicalName())
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
}
