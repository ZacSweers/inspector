package io.sweers.inspector.factorycompiler;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 *
 */
@Target(TYPE) @Retention(SOURCE) public @interface InspectorFactory {
  Class<? extends Annotation>[] include();
}
