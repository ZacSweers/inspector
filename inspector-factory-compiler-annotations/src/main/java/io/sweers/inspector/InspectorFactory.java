package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotation to indicate that a given class should generate a concrete implementation of a
 * {@link Validator.Factory} that handles all the publicly denoted validator implementations of this
 * project.
 * <p>
 * <code><pre>
 *   &#64;InspectorFactory(include = AutoValue.class)
 *   public abstract class Factory implements Validator.Factory {
 *     public static Factory create() {
 *       return new InspectorFactory_Factory();
 *     }
 *   }
 * </pre></code>
 */
@Target(TYPE) @Retention(SOURCE) public @interface InspectorFactory {
  /**
   * @return an array of annotation types that should be included in this factory.
   */
  Class<? extends Annotation>[] include();
}
