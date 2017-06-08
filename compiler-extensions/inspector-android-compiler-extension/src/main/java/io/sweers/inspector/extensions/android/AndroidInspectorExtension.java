package io.sweers.inspector.extensions.android;

import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.Size;
import android.support.annotation.StringDef;
import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import io.sweers.inspector.ValidationException;
import io.sweers.inspector.compiler.plugins.spi.AbstractInspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.InspectorExtension;
import io.sweers.inspector.compiler.plugins.spi.Property;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

@AutoService(InspectorExtension.class) public final class AndroidInspectorExtension
    extends AbstractInspectorExtension {

  private static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS =
      Sets.newLinkedHashSet(Arrays.asList(FloatRange.class, IntRange.class, Size.class));

  private static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS_OF_ANNOTATIONS =
      Sets.newLinkedHashSet(Arrays.asList(IntDef.class, StringDef.class));

  @Override public boolean applicable(ExecutableElement property) {
    for (Class<? extends Annotation> a : SUPPORTED_ANNOTATIONS) {
      if (property.getAnnotation(a) != null) {
        return true;
      }
    }
    for (Class<? extends Annotation> a : SUPPORTED_ANNOTATIONS_OF_ANNOTATIONS) {
      if (findAnnotationByAnnotation(property.getAnnotationMirrors(), a) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public CodeBlock generateValidation(Property prop, String variableName, ParameterSpec value) {
    return addAndroidChecks(prop, variableName);
  }

  private static CodeBlock addAndroidChecks(Property prop, String variableName) {
    CodeBlock.Builder validationBlock = CodeBlock.builder();
    IntRange intRange = prop.annotation(IntRange.class);
    if (intRange != null) {
      long from = intRange.from();
      long to = intRange.to();
      if (from != Long.MIN_VALUE) {
        validationBlock.beginControlFlow("if ($L < $L)", variableName, from)
            .addStatement("throw new $T(\"$L must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                from,
                variableName)
            .endControlFlow();
      }
      if (to != Long.MAX_VALUE) {
        validationBlock.beginControlFlow("else if ($L > $L)", variableName, to)
            .addStatement("throw new $T(\"$L must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                to,
                variableName)
            .endControlFlow();
      }
    }
    FloatRange floatRange = prop.annotation(FloatRange.class);
    if (floatRange != null) {
      double from = floatRange.from();
      double to = floatRange.to();
      if (from != Double.NEGATIVE_INFINITY) {
        validationBlock.beginControlFlow("if ($L < $L)", variableName, from)
            .addStatement("throw new $T(\"$L must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                from,
                variableName)
            .endControlFlow();
      }
      if (to != Double.POSITIVE_INFINITY) {
        validationBlock.beginControlFlow("else if ($L > $L)", variableName, to)
            .addStatement("throw new $T(\"$L must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                to,
                variableName)
            .endControlFlow();
      }
    }
    Size size = prop.annotation(Size.class);
    if (size != null) {
      String sizeVar = variableName + "Size";
      if (prop.type instanceof ArrayTypeName) {
        validationBlock.addStatement("int $L = $L.length", sizeVar, variableName);
      } else if (prop.type instanceof ParameterizedTypeName) {
        // Assume it's a collection or map
        validationBlock.addStatement("int $L = $L.size()", sizeVar, variableName);
      }
      long exact = size.value();
      long min = size.min();
      long max = size.max();
      long multiple = size.multiple();
      if (exact != -1) {
        validationBlock.beginControlFlow("if ($L != $L)", sizeVar, exact)
            .addStatement("throw new $T(\"$L's size must be exactly $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                exact,
                sizeVar)
            .endControlFlow();
      }
      if (min != Long.MIN_VALUE) {
        validationBlock.beginControlFlow("if ($L < $L)", sizeVar, min)
            .addStatement("throw new $T(\"$L's size must be greater than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                min,
                sizeVar)
            .endControlFlow();
      }
      if (max != Long.MAX_VALUE) {
        validationBlock.beginControlFlow("if ($L > $L)", sizeVar, max)
            .addStatement("throw new $T(\"$L's size must be less than $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                max,
                sizeVar)
            .endControlFlow();
      }
      if (multiple != 1) {
        validationBlock.beginControlFlow("if ($L % $L != 0)", sizeVar, multiple)
            .addStatement("throw new $T(\"$L's size must be a multiple of $L but is \" + $L)",
                ValidationException.class,
                prop.methodName,
                multiple,
                sizeVar)
            .endControlFlow();
      }
    }

    IntDef intDef = findAnnotationByAnnotation(prop.element.getAnnotationMirrors(), IntDef.class);
    if (intDef != null) {
      long[] values = intDef.value();
      validationBlock.beginControlFlow("if (!($L))",
          String.join(" && ",
              Longs.asList(values)
                  .stream()
                  .map(l -> variableName + " != " + l)
                  .collect(Collectors.toList())))
          .addStatement("throw new $T(\"$L's value must be within scope of its IntDef. Is \" + $L)",
              ValidationException.class,
              prop.methodName,
              variableName)
          .endControlFlow();
    }
    StringDef stringDef =
        findAnnotationByAnnotation(prop.element.getAnnotationMirrors(), StringDef.class);
    if (stringDef != null) {
      String[] values = stringDef.value();
      validationBlock.beginControlFlow("if (!($L))",
          String.join(" && ",
              Arrays.stream(values)
                  .map(s -> "\"" + s + "\".equals(" + variableName + ")")
                  .collect(Collectors.toList())))
          .addStatement(
              "throw new $T(\"$L's value must be within scope of its StringDef. Is \" + $L)",
              ValidationException.class,
              prop.methodName,
              variableName)
          .endControlFlow();
    }

    return validationBlock.build();
  }

  @Nullable
  private static <T extends Annotation> T findAnnotationByAnnotation(Collection<? extends
      AnnotationMirror> annotations,
      Class<T> clazz) {
    if (annotations.isEmpty()) return null; // Save an iterator in the common case.
    for (AnnotationMirror mirror : annotations) {
      Annotation target = mirror.getAnnotationType()
          .asElement()
          .getAnnotation(clazz);
      if (target != null) {
        //noinspection unchecked
        return (T) target;
      }
    }
    return null;
  }
}
