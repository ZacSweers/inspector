package io.sweers.inspector.autovalue;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public final class AutoValueInspectorTest {

  @Test public void test() {

    JavaFileObject person = JavaFileObjects.forResource("Person.java");
    JavaFileObject dataValidator = JavaFileObjects.forResource("DateValidator.java");

    assertAbout(javaSources()).that(Arrays.asList(person, dataValidator))
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("AutoValue_Person.java"));
  }
}
