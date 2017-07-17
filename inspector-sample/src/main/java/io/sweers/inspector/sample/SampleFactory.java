package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Validator;
import io.sweers.inspector.InspectorFactory;

@InspectorFactory(include = AutoValue.class) public abstract class SampleFactory
    implements Validator.Factory {

  public static SampleFactory create() {
    return new InspectorFactory_SampleFactory();
  }
}
