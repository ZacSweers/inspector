package io.sweers.inspector.autovalue;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Retention(SOURCE) @Target(METHOD) public @interface ValidatedBy {
  // A Validator or Validator.Factory
  Class<?> value();
}
