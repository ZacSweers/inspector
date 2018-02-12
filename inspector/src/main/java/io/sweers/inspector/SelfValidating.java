package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An interface that a given type can implement to let Inspector know that it validates itself.
 *
 * <pre><code>
 *   class Foo implements SelfValidating {
 *      &#64;Override public void validate(Inspector inspector) throws ValidationException {
 *        // Implement custom validation logic here.
 *      }
 *   }
 * </code></pre>
 */
public interface SelfValidating {
  /**
   * Validates this object with whatever custom validation implementation the user wants.
   *
   * @throws ValidationException upon invalidation
   */
  void validate(Inspector inspector) throws ValidationException;

  /**
   * A factory instance for this. This is not considered public API.
   */
  Validator.Factory FACTORY = new Validator.Factory() {

    @Nullable @Override public Validator<?> create(final Type type,
        final Set<? extends Annotation> annotations,
        final Inspector inspector) {
      if (SelfValidating.class.isAssignableFrom(Types.getRawType(type))) {
        return new Validator<SelfValidating>() {
          @Override public void validate(SelfValidating target) throws ValidationException {
            target.validate(inspector);
          }

          @Override public String toString() {
            return "SelfValidating(" + Types.typeToString(type) + ")";
          }
        };
      }
      return null;
    }
  };
}
