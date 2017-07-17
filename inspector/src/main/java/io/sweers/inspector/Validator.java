package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Validates objects of a given type.
 *
 * @param <T> the type to validate.
 */
public abstract class Validator<T> {

  /**
   * Validates a given {@code object} instance
   *
   * @param validationTarget the instance
   * @throws ValidationException upon invalidation
   */
  public abstract void validate(T validationTarget) throws ValidationException;

  public final boolean isValid(T validationTarget) {
    try {
      validate(validationTarget);
      return true;
    } catch (ValidationException e) {
      return false;
    }
  }

  /**
   * @return a nullsafe validator that ignores null instances.
   */
  public Validator<T> nullSafe() {
    final Validator<T> delegate = this;
    return new Validator<T>() {
      @Override public void validate(T validationTarget) throws ValidationException {
        if (validationTarget != null) {
          delegate.validate(validationTarget);
        }
      }

      @Override public String toString() {
        return delegate + ".nullSafe()";
      }
    };
  }

  public interface Factory {
    /**
     * Attempts to create an adapter for {@code type} annotated with {@code annotations}. This
     * returns the adapter if one was created, or null if this factory isn't capable of creating
     * such an adapter.
     *
     * <p>Implementations may use to {@link Inspector#validator} to compose adapters of other types,
     * or {@link Inspector#nextValidator} to delegate to the underlying adapter of the same type.
     */
    @Nullable Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector);
  }
}
