package io.sweers.inspector.sample.integrationtest;

import com.google.auto.value.AutoValue;
import io.sweers.inspector.Inspector;
import io.sweers.inspector.Validator;
import io.sweers.inspector.compiler.annotations.GenerateValidator;
import java.lang.reflect.Type;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class IntegrationTest {

  @Test public void testGenerateValidator() {
    Inspector inspector = new Inspector.Builder().add(GenerateValidator.FACTORY)
        .build();

    assertThat(inspector.validator(GenerateValidatorClazz.class)).isInstanceOf(
        Validator_GenerateValidatorClazz.class);
  }

  @GenerateValidator @AutoValue abstract static class GenerateValidatorClazz {

    abstract int foo();
  }

  @AutoValue abstract static class GenerateValidatorClazzStatic {

    abstract int foo();

    static Validator<GenerateValidatorClazzStatic> validator(Inspector inspector) {
      return new Validator_GenerateValidatorClazzStatic(inspector);
    }
  }

  @Test public void testStaticValidatorGeneric() {
    Inspector inspector = new Inspector.Builder().add(GenerateValidator.FACTORY)
        .build();

    assertThat(inspector.validator(GenerateValidatorClazz.class)).isInstanceOf(
        Validator_GenerateValidatorClazz.class);
  }

  @AutoValue abstract static class GenerateValidatorClazzStaticGeneric<T> {

    abstract int foo();

    static <T> Validator<GenerateValidatorClazzStaticGeneric<T>> validator(Inspector inspector,
        Type[] types) {
      return new Validator_GenerateValidatorClazzStaticGeneric<>(inspector, types);
    }
  }
}
