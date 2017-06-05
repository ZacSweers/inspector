package io.sweers.inspector.sample;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.ValidatedBy;
import java.util.Date;

@AutoValue public abstract class Person {

  public abstract String firstName();

  public abstract String lastName();

  @IntRange(from = 0) public abstract int age();

  @Nullable public abstract String occupation();

  @ValidatedBy(DateValidator.class) public abstract Date birthday();

  @InspectorIgnored public abstract String uuid();

  public static Validator<Person> validator(Inspector inspector) {
    return null;
  }
}
