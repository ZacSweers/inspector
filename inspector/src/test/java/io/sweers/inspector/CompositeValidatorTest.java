package io.sweers.inspector;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class CompositeValidatorTest {

  private final Validator<Integer> concreteValidator =
      CompositeValidator.of(new GreaterThan5Validator(),
          new LessThan10Validator(),
          new PositiveValidator());

  private final Validator<Foo> reflectiveValidator = new Inspector.Builder().build()
      .validator(Foo.class);

  @Test public void concrete_valid() {
    try {
      concreteValidator.validate(5);
      concreteValidator.validate(6);
      concreteValidator.validate(7);
      concreteValidator.validate(8);
      concreteValidator.validate(9);
      concreteValidator.validate(10);
    } catch (ValidationException e) {
      throw new AssertionError("This should be valid!");
    }
  }

  @Test public void concrete_invalid() {
    try {
      concreteValidator.validate(4);
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat()
          .isEqualTo("Was less than 5");
    }

    try {
      concreteValidator.validate(11);
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat()
          .isEqualTo("Was greater than 10");
    }
  }

  @Test public void concrete_compositeValidation() {
    try {
      concreteValidator.validate(-1);
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).isInstanceOf(CompositeValidationException.class);
      CompositeValidationException ce = (CompositeValidationException) e;
      assertThat(ce.getExceptions()).hasSize(2);
      assertThat(e).hasMessageThat()
          .contains("Was less than 5");
      assertThat(e).hasMessageThat()
          .contains("Was negative");
    }
  }

  @Test public void reflective_valid() {
    try {
      reflectiveValidator.validate(new Foo(5));
      reflectiveValidator.validate(new Foo(6));
      reflectiveValidator.validate(new Foo(7));
      reflectiveValidator.validate(new Foo(8));
      reflectiveValidator.validate(new Foo(9));
      reflectiveValidator.validate(new Foo(10));
    } catch (ValidationException e) {
      throw new AssertionError("This should be valid!");
    }
  }

  @Test public void reflective_invalid() {
    try {
      reflectiveValidator.validate(new Foo(4));
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat()
          .isEqualTo("Was less than 5");
    }

    try {
      reflectiveValidator.validate(new Foo(11));
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat()
          .isEqualTo("Was greater than 10");
    }
  }

  @Test public void reflective_compositeValidation() {
    try {
      reflectiveValidator.validate(new Foo(-1));
      throw new AssertionError("This should be invalid!");
    } catch (ValidationException e) {
      assertThat(e).isInstanceOf(CompositeValidationException.class);
      CompositeValidationException ce = (CompositeValidationException) e;
      assertThat(ce.getExceptions()).hasSize(2);
      assertThat(e).hasMessageThat()
          .contains("Was less than 5");
      assertThat(e).hasMessageThat()
          .contains("Was negative");
    }
  }

  static class PositiveValidator extends Validator<Integer> {
    @Override public void validate(Integer integer) throws ValidationException {
      if (integer < 0) {
        throw new ValidationException("Was negative");
      }
    }
  }

  static class GreaterThan5Validator extends Validator<Integer> {
    @Override public void validate(Integer integer) throws ValidationException {
      if (integer < 5) {
        throw new ValidationException("Was less than 5");
      }
    }
  }

  static class LessThan10Validator extends Validator<Integer> {
    @Override public void validate(Integer integer) throws ValidationException {
      if (integer > 10) {
        throw new ValidationException("Was greater than 10");
      }
    }
  }

  static class Foo {

    final int value;

    public Foo(int value) {
      this.value = value;
    }

    @ValidatedBy({
        GreaterThan5Validator.class, LessThan10Validator.class, PositiveValidator.class
    })
    public int value() {
      return value;
    }
  }
}
