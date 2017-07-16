package io.sweers.inspector.factorycompiler;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.testing.compile.JavaFileObjects;
import io.sweers.inspector.compiler.InspectorProcessor;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public final class InspectorFactoryProcessorTest {

  @Test public void smokeTest() {
    JavaFileObject factory = JavaFileObjects.forResource("MyFactory.java");
    JavaFileObject person = JavaFileObjects.forResource("Person.java");
    JavaFileObject person2 = JavaFileObjects.forResource("PersonTwo.java");

    // Person 3 has no validator method, but that's a-ok! We should just ignore it
    JavaFileObject person3 = JavaFileObjects.forResource("PersonThree.java");

    assertAbout(javaSources()).that(Arrays.asList(factory, person, person2, person3))
        .withClasspathFrom(getClass().getClassLoader())
        .processedWith(new InspectorProcessor(),
            new AutoValueProcessor(),
            new InspectorFactoryProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("InspectorFactory_MyFactory.java"));
  }
}
