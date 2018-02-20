package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;

@AutoValue public abstract class PersonTwo {

  public abstract String firstName();

  public static Validator<PersonTwo> validator(Inspector inspector) {
    return new PersonTwoValidator(inspector);
  }
}
