package io.sweers.inspector.compiler.plugins.spi;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Empty extension implementation
 */
public abstract class AbstractInspectorExtension implements InspectorExtension {

  @Override public Set<String> applicableAnnotations() {
    return Collections.emptySet();
  }

  @Override public boolean applicable(Property property) {
    return false;
  }

  @Override @Nullable
  public CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value) {
    return null;
  }

  @Override public Priority priority() {
    return Priority.NONE;
  }

  @Override public String toString() {
    return getClass().getName();
  }
}
