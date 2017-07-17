package io.sweers.inspector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Validates collections. */
class CollectionValidator<C extends Collection<T>, T> extends Validator<C> {
  public static final Validator.Factory FACTORY = new Validator.Factory() {
    @Override public @Nullable Validator<?> create(Type type,
        Set<? extends Annotation> annotations,
        Inspector inspector) {
      Class<?> rawType = Types.getRawType(type);
      if (!annotations.isEmpty()) return null;
      if (rawType == List.class || rawType == Collection.class || rawType == Set.class) {
        return newCollectionValidator(type, inspector).nullSafe();
      }
      return null;
    }
  };

  private final Validator<T> elementValidator;

  private CollectionValidator(Validator<T> elementValidator) {
    this.elementValidator = elementValidator;
  }

  static <T> Validator<Collection<T>> newCollectionValidator(Type type, Inspector inspector) {
    Type elementType = Types.collectionElementType(type, Collection.class);
    Validator<T> elementValidator = inspector.validator(elementType);
    return new CollectionValidator<>(elementValidator);
  }

  @Override public void validate(C validationTarget) throws ValidationException {
    for (T element : validationTarget) {
      elementValidator.validate(element);
    }
  }

  @Override public String toString() {
    return elementValidator + ".collection()";
  }
}
