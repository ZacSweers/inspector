package io.sweers.inspector;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * A convenience {@link Validator} that can compose multiple validators.
 */
public final class CompositeValidator<T> extends Validator<T> {

  @SafeVarargs public static <T> CompositeValidator<T> of(Validator<? super T>... validators) {
    if (validators == null) {
      throw new NullPointerException("No validators received!");
    }
    return of(asList(validators));
  }

  public static <T> CompositeValidator<T> of(Iterable<Validator<? super T>> validators) {
    if (validators == null) {
      throw new NullPointerException("validators are null");
    }
    ArrayList<Validator<? super T>> list = new ArrayList<>();
    for (Validator<? super T> validator : validators) {
      list.add(validator);
    }
    return new CompositeValidator<>(list);
  }

  public static <T> CompositeValidator<T> of(List<Validator<? super T>> validators) {
    if (validators == null) {
      throw new NullPointerException("validators are null");
    }
    return new CompositeValidator<>(unmodifiableList(validators));
  }

  private final List<Validator<? super T>> validators;

  private CompositeValidator(List<Validator<? super T>> validators) {
    this.validators = validators;
  }

  @Override public void validate(T t) throws CompositeValidationException {
    List<ValidationException> exceptions = new ArrayList<>();
    for (Validator<? super T> validator : validators) {
      try {
        validator.validate(t);
      } catch (ValidationException e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      if (exceptions.size() == 1) {
        throw exceptions.get(0);
      } else {
        throw new CompositeValidationException(exceptions);
      }
    }
  }
}
