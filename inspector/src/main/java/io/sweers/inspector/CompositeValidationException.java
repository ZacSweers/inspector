package io.sweers.inspector;

import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 * A composite {@link ValidationException} that can hold and report multiple exceptions.
 */
public class CompositeValidationException extends ValidationException {

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
