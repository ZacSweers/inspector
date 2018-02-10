package io.sweers.inspector;

import io.sweers.inspector.InspectorTest.SelfValidatingType.NestedInheritedSelfValidating;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.sweers.inspector.InspectorTest.SelfValidatingType.VALIDATION_MESSAGE;
import static junit.framework.TestCase.fail;

public final class InspectorTest {

  @Test
  public void test() {
    Inspector inspector = new Inspector.Builder()
        .build();

    Data data = new Data();
    try {
      inspector.validator(Data.class).validate(data);
      fail("No validation was run");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("thing() was null");
    }

    List<Data> dataList = Arrays.asList(new Data());
    try {
      inspector.validator(Types.newParameterizedType(List.class, Data.class)).validate(dataList);
      fail("No validation was run");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("thing() was null");
    }
  }

  public static class Data {
    public String thing() {
      return null;
    }
  }

  @Test public void testSelfValidating() {
    Inspector inspector = new Inspector.Builder()
        .build();

    SelfValidatingType selfValidatingType = new SelfValidatingType();

    try {
      inspector.validator(SelfValidatingType.class).validate(selfValidatingType);
      fail("This should throw a validation exception!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().isEqualTo(VALIDATION_MESSAGE);
    }

    NestedInheritedSelfValidating nested = new NestedInheritedSelfValidating();

    try {
      inspector.validator(NestedInheritedSelfValidating.class).validate(nested);
      fail("This should throw a validation exception!");
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().isEqualTo(VALIDATION_MESSAGE);
    }
  }

  interface InheritedSelfValidating extends SelfValidating {

  }

  static class SelfValidatingType implements InheritedSelfValidating {
    static final String VALIDATION_MESSAGE = "This should fail!";
    @Override public void validate(Inspector inspector) throws ValidationException {
      throw new ValidationException(VALIDATION_MESSAGE);
    }

    static class NestedInheritedSelfValidating extends SelfValidatingType {

    }
  }

}
