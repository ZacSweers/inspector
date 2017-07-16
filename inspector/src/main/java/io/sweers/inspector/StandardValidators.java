package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

final class StandardValidators {

  public static final Validator.Factory FACTORY = new Validator.Factory() {
    @Override public Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      if (!annotations.isEmpty()) return null;
      if (type == boolean.class) return NO_OP_VALIDATOR;
      if (type == byte.class) return NO_OP_VALIDATOR;
      if (type == char.class) return NO_OP_VALIDATOR;
      if (type == double.class) return NO_OP_VALIDATOR;
      if (type == float.class) return NO_OP_VALIDATOR;
      if (type == int.class) return NO_OP_VALIDATOR;
      if (type == long.class) return NO_OP_VALIDATOR;
      if (type == short.class) return NO_OP_VALIDATOR;
      if (type == Boolean.class) return NO_OP_VALIDATOR;
      if (type == Byte.class) return NO_OP_VALIDATOR;
      if (type == Character.class) return NO_OP_VALIDATOR;
      if (type == Double.class) return NO_OP_VALIDATOR;
      if (type == Float.class) return NO_OP_VALIDATOR;
      if (type == Integer.class) return NO_OP_VALIDATOR;
      if (type == Long.class) return NO_OP_VALIDATOR;
      if (type == Short.class) return NO_OP_VALIDATOR;
      if (type == String.class) return NO_OP_VALIDATOR;
      if (type == Object.class) return NO_OP_VALIDATOR;
      return null;
    }
  };

  @SuppressWarnings("WeakerAccess") // Synthetic accessor
  static final Validator<Object> NO_OP_VALIDATOR = new Validator<Object>() {
    @Override public void validate(Object object) throws ValidationException {
      // Nothing to do
    }
  };
}
