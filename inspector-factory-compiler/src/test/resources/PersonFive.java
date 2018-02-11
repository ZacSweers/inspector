package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.Validator;
import java.lang.reflect.Type;

@AutoValue public abstract class PersonFive<T, V> {

  public abstract String firstName();
  public abstract T attribute();
  public abstract V attribute2();

  public static <T, V> Validator<PersonFive<T, V>> validator(Inspector inspector, Type[] types) {
    return new Validator<PersonFive<T, V>>() {
      @Override public void validate(PersonFive<T, V> personFour) throws ValidationException {

      }
    };
  }
}
