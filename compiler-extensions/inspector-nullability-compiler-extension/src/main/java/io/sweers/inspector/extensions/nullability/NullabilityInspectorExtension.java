package io.sweers.inspector.extensions.nullability;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.compiler.plugins.spi.AbstractInspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;

@AutoService(InspectorExtension.class) public final class NullabilityInspectorExtension
    extends AbstractInspectorExtension {

  @Override public boolean applicable(Property property) {
    return !property.type.isPrimitive() && !property.annotations.contains("Nullable");
  }

  @Override
  public CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value) {
    return CodeBlock.builder()
        .beginControlFlow("if ($L == null)", variableName)
        .addStatement("throw new $T($S)",
            ValidationException.class,
            prop.methodName + "() is not nullable but returns a null")
        .endControlFlow()
        .build();
  }

  @Override public Priority priority() {
    return Priority.HIGH;
  }

  @Override public String toString() {
    return NullabilityInspectorExtension.class.getSimpleName();
  }
}
