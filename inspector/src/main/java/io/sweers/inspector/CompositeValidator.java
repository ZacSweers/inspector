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
      throw new CompositeValidationException(exceptions);
    }
  }

  /**
   * A composite {@link ValidationException} that can hold and report multiple exceptions.
   */
  public static class CompositeValidationException extends ValidationException {
    private final List<ValidationException> exceptions;

    CompositeValidationException(List<ValidationException> exceptions) {
      super(createMessage(exceptions));
      this.exceptions = exceptions;
    }

    /**
     * @return the list of discovered exceptions.
     */
    public List<ValidationException> getExceptions() {
      return unmodifiableList(exceptions);
    }

    private static String createMessage(List<ValidationException> exceptions) {
      StringBuilder builder = new StringBuilder();
      builder.append("Multiple validation exceptions found! Exceptions: [\n")
          .append(exceptions.get(0)
              .getMessage());
      if (exceptions.size() > 1) {
        for (int i = 1; i < exceptions.size(); i++) {
          ValidationException exception = exceptions.get(i);
          builder.append(",\n")
              .append(exception.getMessage());
        }
      }
      return builder.append("\n]")
          .toString();
    }
  }
}
