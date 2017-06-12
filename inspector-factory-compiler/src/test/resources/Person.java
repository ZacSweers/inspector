package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;

@AutoValue public abstract class Person {

  public abstract String firstName();

  public static Validator<Person> validator(Inspector inspector) {
    return new Validator_Person(inspector);
  }
}
