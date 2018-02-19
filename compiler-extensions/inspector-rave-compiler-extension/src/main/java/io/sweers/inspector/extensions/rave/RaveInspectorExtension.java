package io.sweers.inspector.extensions.rave;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.uber.rave.annotation.MustBeFalse;
import com.uber.rave.annotation.MustBeTrue;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;
import java.util.Objects;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

@AutoService(InspectorExtension.class) public final class RaveInspectorExtension
    implements InspectorExtension {
  @Override public boolean applicable(Property property) {
    return property.element.getAnnotation(MustBeTrue.class) != null
        || property.element.getAnnotation(MustBeFalse.class) != null;
  }

  @Override
  public CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value) {
    // TODO this isn't right -_-
    return addRaveChecks(((ExecutableElement) prop.element), prop.methodName, value);
  }

  @Override public String toString() {
    return RaveInspectorExtension.class.getSimpleName();
  }

  private static CodeBlock addRaveChecks(ExecutableElement element,
      String methodName,
      ParameterSpec value) {
    CodeBlock.Builder validationBlock = CodeBlock.builder();
    MustBeTrue mustBeTrue = element.getAnnotation(MustBeTrue.class);
    if (mustBeTrue != null) {
      TypeMirror rType = element.getReturnType();
      TypeName returnType = TypeName.get(rType);
      if (!Objects.equals(returnType, TypeName.BOOLEAN) && !Objects.equals(returnType,
          TypeName.BOOLEAN.box())) {
        throw new IllegalArgumentException(
            "@MustBeTrue can only be used on boolean return types but "
                + methodName
                + " returns "
                + returnType);
      }
      validationBlock.beginControlFlow("if (!$N.$L())", value, methodName)
          .addStatement("throw new $T(\"$L must be true but is false\")",
              ValidationException.class,
              methodName)
          .endControlFlow();
    }
    MustBeFalse mustBeFalse = element.getAnnotation(MustBeFalse.class);
    if (mustBeFalse != null) {
      TypeMirror rType = element.getReturnType();
      TypeName returnType = TypeName.get(rType);
      if (!Objects.equals(returnType, TypeName.BOOLEAN) && !Objects.equals(returnType,
          TypeName.BOOLEAN.box())) {
        throw new IllegalArgumentException(
            "@MustBeFalse can only be used on boolean return types but "
                + methodName
                + " returns "
                + returnType);
      }
      validationBlock.beginControlFlow("if ($N.$L())", value, methodName)
          .addStatement("throw new $T(\"$L must be false but is true\")",
              ValidationException.class,
              methodName)
          .endControlFlow();
    }
    return validationBlock.build();
  }
}
