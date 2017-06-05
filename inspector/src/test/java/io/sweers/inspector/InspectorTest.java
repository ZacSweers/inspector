package io.sweers.inspector;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
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

}
