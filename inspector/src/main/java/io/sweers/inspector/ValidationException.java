package io.sweers.inspector;

/**
 * Base class for validation exceptions. You can use this directly with your own message if you
 * want, or subclass it to put your own information.
 */
public class ValidationException extends RuntimeException {
  public ValidationException() {
  }

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ValidationException(Throwable cause) {
    super(cause);
  }

  public ValidationException(String message,
      Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
