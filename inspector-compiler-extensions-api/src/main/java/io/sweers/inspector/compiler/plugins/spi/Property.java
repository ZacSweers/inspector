package io.sweers.inspector.compiler.plugins.spi;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.TypeName;
import com.squareup.kotlinpoet.TypeNames;
import io.sweers.inspector.InspectorIgnored;
import io.sweers.inspector.ValidatedBy;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class Property {
  public final String methodName;
  public final String humanName;
  public final Element element;

  /**
   * @deprecated Use {@link #javaTypeName}.
   */
  @Deprecated
  public final TypeName type;

  public final TypeMirror typeMirror;
  public final TypeName javaTypeName;
  public final com.squareup.kotlinpoet.TypeName kotlinTypeName;
  public final ImmutableSet<String> annotations;

  public Property(String humanName, Element element) {
    this.methodName = element.getSimpleName()
        .toString();
    this.humanName = humanName;
    this.element = element;

    if (element instanceof ExecutableElement) {
      typeMirror = ((ExecutableElement) element).getReturnType();
    } else {
      typeMirror = element.asType();
    }
    javaTypeName = TypeName.get(typeMirror);
    type = javaTypeName;
    kotlinTypeName = TypeNames.get(typeMirror);
    annotations = buildAnnotations(element);
  }

  @Nullable static TypeMirror getAnnotationValue(Element foo, Class<?> annotation) {
    AnnotationMirror am = getAnnotationMirror(foo, annotation);
    if (am == null) {
      return null;
    }
    AnnotationValue av = getAnnotationValue(am, "value");
    return av == null ? null : (TypeMirror) av.getValue();
  }

  @Nullable
  private static AnnotationMirror getAnnotationMirror(Element typeElement, Class<?> clazz) {
    String clazzName = clazz.getName();
    for (AnnotationMirror m : typeElement.getAnnotationMirrors()) {
      if (m.getAnnotationType()
          .toString()
          .equals(clazzName)) {
        return m;
      }
    }
    return null;
  }

  @Nullable
  private static AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror, String key) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> values =
        annotationMirror.getElementValues();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values
        .entrySet()) {
      if (entry.getKey()
          .getSimpleName()
          .toString()
          .equals(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable public <T extends Annotation> T annotation(Class<T> annotation) {
    return element.getAnnotation(annotation);
  }

  @Nullable public ValidatedBy validatedBy() {
    return element.getAnnotation(ValidatedBy.class);
  }

  @Nullable public AnnotationMirror validatedByMirror() {
    return getAnnotationMirror(element, ValidatedBy.class);
  }

  public boolean shouldValidate() {
    return element.getAnnotation(InspectorIgnored.class) == null && validatedBy() == null;
  }

  private ImmutableSet<String> buildAnnotations(Element element) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();

    List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();
    for (AnnotationMirror annotation : annotations) {
      builder.add(annotation.getAnnotationType()
          .asElement()
          .getSimpleName()
          .toString());
    }

    return builder.build();
  }
}
