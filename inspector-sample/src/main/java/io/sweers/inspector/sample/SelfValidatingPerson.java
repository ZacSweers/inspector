package io.sweers.inspector.sample;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.SelfValidating;
import io.sweers.inspector.ValidationException;

@AutoValue public abstract class SelfValidatingPerson implements SelfValidating {

  @Override public final void validate(Inspector inspector) throws ValidationException {
    // Great!
  }
}
