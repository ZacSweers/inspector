package io.sweers.inspector.sample;

import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.LongDef;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.StringDef;
import com.google.auto.value.AutoValue;
import com.uber.rave.annotation.MustBeFalse;
import com.uber.rave.annotation.MustBeTrue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.ValidatedBy;
import io.sweers.inspector.Validator;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoValue public abstract class Person<T, V> {

  public static final String FOO = "foo";
  public static final String FOO2 = "foo2";
  public static final int FOO_INT = 0;
  public static final long FOO_LONG = 0L;

  @StringDef({ FOO, FOO2 }) public @interface StringDefChecked {}

  @IntDef(FOO_INT) public @interface IntDefChecked {}

  @LongDef(FOO_LONG) public @interface LongDefChecked {}

  public abstract String firstName();

  public abstract String lastName();

  @SuppressWarnings("mutable") public abstract int[] favoriteNumbers();

  public abstract List<String> aList();

  public abstract Map<String, String> aMap();

  public abstract Set<String> favoriteFoods();

  @StringDefChecked public abstract String stringDefChecked();

  @IntDefChecked public abstract int intDefChecked();

  @LongDefChecked public abstract long longDefChecked();

  @IntRange(from = 0) public abstract int age();

  @Nullable public abstract String occupation();

  @ValidatedBy({ DateValidator.class, SecondaryDateValidator.class })
  public abstract Date birthday();

  @InspectorIgnored public abstract String uuid();

  @Size(multiple = 2) public abstract List<String> doublesOfStrings();

  @Size(3) public abstract Map<String, String> threePairs();

  @Size(min = 3) public abstract Set<String> atLeastThreeStrings();

  @Size(max = 3) public abstract Set<String> atMostThreeStrings();

  @MustBeTrue public final boolean checkMustBeTrue() {
    return true;
  }

  @MustBeFalse public final boolean checkMustBeFalse() {
    return false;
  }

  public abstract T genericOne();

  public abstract V genericTwo();

  public static <T, V> Validator<Person<T, V>> validator(Inspector inspector, Type[] types) {
    return new Validator_Person<>(inspector, types);
  }
}
