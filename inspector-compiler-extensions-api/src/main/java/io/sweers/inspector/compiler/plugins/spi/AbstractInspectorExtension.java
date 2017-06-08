package io.sweers.inspector.compiler.plugins.spi;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;

/**
 * Empty extension implementation
 */
public abstract class AbstractInspectorExtension implements InspectorExtension {

  @Override public Set<String> applicableAnnotations() {
    return Collections.emptySet();
  }

  @Override public boolean applicable(ExecutableElement property) {
    return false;
  }

  @Override @Nullable
  public CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value) {
    return null;
  }
}
