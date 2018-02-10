package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.Validator;

@AutoValue public abstract class PersonFour {

  public abstract String firstName();

  public static Validator<PersonFour> validator() {
    return new Validator<PersonFour>() {
      @Override public void validate(PersonFour personFour) throws ValidationException {

      }
    };
  }
}
