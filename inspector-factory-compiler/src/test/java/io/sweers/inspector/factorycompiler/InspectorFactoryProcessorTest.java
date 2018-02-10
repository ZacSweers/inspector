package io.sweers.inspector.factorycompiler;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.testing.compile.JavaFileObjects;
import io.sweers.inspector.compiler.InspectorProcessor;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static java.util.Arrays.asList;

public final class InspectorFactoryProcessorTest {

  @Test public void smokeTest() {
    JavaFileObject factory = JavaFileObjects.forResource("MyFactory.java");
    JavaFileObject person = JavaFileObjects.forResource("Person.java");
    JavaFileObject person2 = JavaFileObjects.forResource("PersonTwo.java");

    // Person 3 has no validator method, but that's a-ok! We should just ignore it
    JavaFileObject person3 = JavaFileObjects.forResource("PersonThree.java");

    // Person 4 has a validator method with no args
    JavaFileObject person4 = JavaFileObjects.forResource("PersonFour.java");

    // Person 5 is generic
    JavaFileObject person5 = JavaFileObjects.forResource("PersonFive.java");

    assertAbout(javaSources()).that(asList(factory, person, person2, person3, person4, person5))
        .withClasspathFrom(getClass().getClassLoader())
        .processedWith(new InspectorProcessor(),
            new AutoValueProcessor(),
            new InspectorFactoryProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("InspectorFactory_MyFactory.java"));
  }
}
