package io.sweers.inspector.compiler;

import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public final class InspectorProcessorTest {

  @Test public void test() {

    JavaFileObject person = JavaFileObjects.forResource("Person.java");
    JavaFileObject dataValidator = JavaFileObjects.forResource("DateValidator.java");

    assertAbout(javaSources()).that(Arrays.asList(person, dataValidator))
        .withClasspathFrom(getClass().getClassLoader())
        .processedWith(new InspectorProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("Validator_Person.java"));
  }
}
