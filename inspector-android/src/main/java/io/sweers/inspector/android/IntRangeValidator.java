package io.sweers.inspector.android;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.Validator;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * A Validator for IntRange. Proof of concept, but not possible for now as these annotations are
 * only source.
 */
public final class IntRangeValidator extends Validator<Integer> {

  public static Validator.Factory FACTORY = new Factory() {
    @Override public Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      if (!annotations.isEmpty()) return null;
      Annotation annotation = findAnnotation(annotations, IntRange.class);
      if (annotation != null) {
        IntRange intRange = (IntRange) annotation;
        return new IntRangeValidator(intRange.from(), intRange.to());
      }
      return null;
    }
  };

  private final long from;
  private final long to;

  public IntRangeValidator(long from, long to) {
    this.from = from;
    this.to = to;
  }

  @Override public void validate(Integer value) throws ValidationException {
    if (value < from || value > to) {
      throw new ValidationException("Value was outside bounds");
    }
  }

  @Nullable public static Annotation findAnnotation(Set<? extends Annotation> annotations,
      Class<? extends Annotation> annotationClass) {
    if (annotations.isEmpty()) return null; // Save an iterator in the common case.
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == annotationClass) return annotation;
    }
    return null;
  }
}
