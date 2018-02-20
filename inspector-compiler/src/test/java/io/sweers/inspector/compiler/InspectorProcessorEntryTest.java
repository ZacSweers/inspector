package io.sweers.inspector.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static javax.tools.JavaFileObject.Kind.SOURCE;

public final class InspectorProcessorEntryTest {

  @Test public void test() {

    JavaFileObject person = JavaFileObjects.forResource("Person.java");
    JavaFileObject dataValidator = JavaFileObjects.forResource("DateValidator.java");

    assertAbout(javaSources()).that(Arrays.asList(person, dataValidator))
        .withClasspathFrom(getClass().getClassLoader())
        .processedWith(new InspectorProcessorEntry())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("PersonValidator.java"));
  }

  @Test public void shouldIgnoreIfImplementsSelfValidating() {
    Compilation compilation = javac().withClasspathFrom(getClass().getClassLoader())
        .withProcessors(new InspectorProcessorEntry())
        .compile(JavaFileObjects.forSourceLines("test.SelfValidatingFoo", "package test;\n"
            + "\n"
            + "import com.google.auto.value.AutoValue;\n"
            + "import io.sweers.inspector.Inspector;\n"
            + "import io.sweers.inspector.SelfValidating;\n"
            + "import io.sweers.inspector.ValidationException;\n"
            + "import io.sweers.inspector.Validator;\n"
            + "\n"
            + "@AutoValue abstract class SelfValidatingFoo implements SelfValidating {\n"
            + "  @Override public final void validate(Inspector inspector) throws "
            + "ValidationException {\n"
            + "    // Custom validation!\n"
            + "  }\n"
            + "  \n"
            + "  public static Validator<SelfValidatingFoo> validator(Inspector inspector) {\n"
            + "    return new Validator<SelfValidatingFoo>() {\n"
            + "      @Override public void validate(SelfValidatingFoo selfValidatingFoo)\n"
            + "          throws ValidationException {\n"
            + "        \n"
            + "      }\n"
            + "    };\n"
            + "  }\n"
            + "}"));

    List<JavaFileObject> generatedJavaFiles = compilation.generatedSourceFiles()
        .stream()
        .filter(javaFileObject -> javaFileObject.getKind() == SOURCE)
        .collect(Collectors.toList());
    assertThat(generatedJavaFiles).isEmpty();
  }
}
