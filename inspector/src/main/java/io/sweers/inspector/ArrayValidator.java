package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Validates arrays.
 */
final class ArrayValidator extends Validator<Object> {
  public static final Factory FACTORY = new Factory() {
    @Override public @Nullable Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      Type elementType = Types.arrayComponentType(type);
      if (elementType == null) return null;
      if (!annotations.isEmpty()) return null;
      Validator<Object> elementValidator = inspector.validator(elementType);
      return new ArrayValidator(elementValidator).nullSafe();
    }
  };

  private final Validator<Object> elementValidator;

  ArrayValidator(Validator<Object> elementValidator) {
    this.elementValidator = elementValidator;
  }

  @Override public void validate(Object object) throws ValidationException {
    for (int i = 0, size = Array.getLength(object); i < size; i++) {
      elementValidator.validate(Array.get(object, i));
    }
  }

  @Override public String toString() {
    return elementValidator + ".array()";
  }
}
