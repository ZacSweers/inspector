package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Validator;
import io.sweers.inspector.factorycompiler.InspectorFactory;

@InspectorFactory(include = AutoValue.class) public abstract class MyFactory
    implements Validator.Factory {
  public static MyFactory create() {
    return new InspectorFactory_MyFactory();
  }
}
