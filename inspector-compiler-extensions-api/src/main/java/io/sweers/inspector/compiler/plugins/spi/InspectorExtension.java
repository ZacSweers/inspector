package io.sweers.inspector.compiler.plugins.spi;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Basic extension interface.
 */
public interface InspectorExtension {

  /**
   * @return any other applicable annotations you want to process.
   */
  Set<String> applicableAnnotations();

  boolean applicable(Property property);

  @Nullable CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value);

  Priority priority();

  enum Priority {
    /**
     * Use this priority to indicate that this must be run as soon as possible, such as nullability
     * checks.
     */
    HIGH,

    /**
     * Use this priority to indicate that sooner is better, but not important.
     */
    NORMAL,

    /**
     * Use this priority to indicate that it does not matter when this runs, as long as it does.
     * This is the default mode.
     */
    NONE
  }
}
