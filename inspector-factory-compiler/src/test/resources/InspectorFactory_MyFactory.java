package io.sweers.inspector.sample;

import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;
import java.lang.Override;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

public final class InspectorFactory_MyFactory extends MyFactory {
  @Override
  public Validator<?> create(Type type, Set<? extends Annotation> annotations,
      Inspector inspector) {
    if (!annotations.isEmpty()) return null;
    if (type.equals(Person.class)) {
      return Person.validator(inspector);
    } else if (type.equals(PersonTwo.class)) {
      return PersonTwo.validator(inspector);
    } else if (type.equals(PersonFour.class)) {
      return PersonFour.validator();
    }
    return null;
  }
}
