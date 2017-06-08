package io.sweers.inspector.compiler.plugins.spi;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Set;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;

/**
 * Basic extension interface.
 */
public interface InspectorExtension {

  /**
   * @return any other applicable annotations you want to process.
   */
  Set<String> applicableAnnotations();

  boolean applicable(ExecutableElement property);

  @Nullable CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value);
}
